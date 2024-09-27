package com.ureca.news.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;

import com.ureca.news.dto.News;
import com.ureca.news.repository.NewsRepository;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.*;
import reactor.core.publisher.Mono;

@Service
public class TopUsService {

    @Value("${newsapi-key}")
    private String API_KEY;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    private NewsRepository newsRepository;

    private static final Set<String> stopWords = Set.of(
        "the", "is", "at", "of", "on", "and", "a", "an", "in", "to", "for", "by", "with", "about", "as", "it", "that"
    );

    public Mono<List<Map<String, Object>>> getTopNews(@RequestParam String category) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime startOfDay = yesterday.atStartOfDay(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime endOfDay = yesterday.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toLocalDateTime();

        List<News> existingNews = newsRepository.findByCategoryAndPublishedAtBetween(category, startOfDay, endOfDay);

        if (!existingNews.isEmpty()) {
            return Mono.just(existingNews.stream().map(news -> {
                Map<String, Object> articleMap = new HashMap<>();
                articleMap.put("title", news.getTitle());
                articleMap.put("url", news.getUrl());
                articleMap.put("urlToImage", news.getUrlToImage());
                articleMap.put("description", news.getDescription());
                articleMap.put("content", news.getContent());
                articleMap.put("publishedAt", news.getPublishedAt().toString());
                return articleMap;
            }).collect(Collectors.toList()));
        }
        return callNewsApiAndStore(category, startOfDay, endOfDay);
    }

    private Mono<List<Map<String, Object>>> callNewsApiAndStore(String category, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedDate = startOfDay.toLocalDate().format(formatter);

        String categoryUrl = "https://newsapi.org/v2/top-headlines?country=us&category=" + category
                + "&from=" + formattedDate + "&to=" + formattedDate + "&pageSize=100&apiKey=" + API_KEY;

        return webClientBuilder.build()
                .get()
                .uri(categoryUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (List<Map<String, Object>>) response.get("articles"))
                .flatMap(articles -> extractTopNouns(articles, category, startOfDay, endOfDay))
                .doOnError(error -> System.err.println("Error: " + error.getMessage()));
    }

    private Mono<List<Map<String, Object>>> extractTopNouns(List<Map<String, Object>> articles, String category, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        Map<String, Integer> nounCount = new HashMap<>();
        List<Map<String, Object>> selectedArticles = new ArrayList<>();

        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma");
        props.setProperty("outputFormat", "json");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        for (Map<String, Object> article : articles) {
            String title = (String) article.get("title");
            String content = (String) article.get("content");

            if (title.contains(" - ")) {
                title = title.substring(0, title.lastIndexOf(" - ")).trim();
            }

            processTextForNouns(title, pipeline, nounCount);
            processTextForNouns(content, pipeline, nounCount);
        }

        List<Map.Entry<String, Integer>> sortedNouns = new ArrayList<>(nounCount.entrySet());
        sortedNouns.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        Set<String> usedLinks = new HashSet<>();
        for (int i = 0; i < sortedNouns.size() && selectedArticles.size() < 5; i++) {
            String topNoun = sortedNouns.get(i).getKey();
            for (Map<String, Object> article : articles) {
                String articleTitle = (String) article.get("title");
                String articleLink = (String) article.get("url");

                String articlePublishedAtStr = (String) article.get("publishedAt");
                LocalDateTime articlePublishedAt = LocalDateTime.parse(articlePublishedAtStr, DateTimeFormatter.ISO_DATE_TIME)
                        .atZone(ZoneOffset.UTC)
                        .withZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime();

                if (articlePublishedAt.isAfter(startOfDay) && articlePublishedAt.isBefore(endOfDay)) {
                    String articleContent = (String) article.get("content");
                    if ((articleTitle.toLowerCase().contains(topNoun) || (articleContent != null && articleContent.toLowerCase().contains(topNoun))) 
                            && !usedLinks.contains(articleLink)) {
                        selectedArticles.add(article);
                        usedLinks.add(articleLink);

                        if (!newsRepository.existsByUrl(articleLink)) {
                            News news = new News();
                            news.setTitle(articleTitle);
                            news.setUrl(articleLink);
                            news.setUrlToImage((String) article.get("urlToImage"));
                            news.setDescription((String) article.get("description"));
                            news.setContent(articleContent);
                            news.setPublishedAt(articlePublishedAt);
                            news.setCategory(category);
                            newsRepository.save(news);
                        }
                        break;
                    }
                }
            }
        }

        if (selectedArticles.size() < 5) {
            for (Map<String, Object> article : articles) {
                String articleLink = (String) article.get("url");
                if (!usedLinks.contains(articleLink)) {
                    selectedArticles.add(article);
                    usedLinks.add(articleLink);
                    if (selectedArticles.size() == 5) {
                        break;
                    }
                }
            }
        }

        return Mono.just(selectedArticles);
    }

    private void processTextForNouns(String text, StanfordCoreNLP pipeline, Map<String, Integer> nounCount) {
        if (text != null) {
            Annotation annotation = new Annotation(text);
            pipeline.annotate(annotation);

            List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
            for (CoreMap sentence : sentences) {
                for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                    String word = token.word().toLowerCase();
                    String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);

                    if ((pos.startsWith("NN") || pos.startsWith("JJ")) && !stopWords.contains(word)) {
                        nounCount.put(word, nounCount.getOrDefault(word, 0) + 1);
                    }
                }
            }
        }
    }
}