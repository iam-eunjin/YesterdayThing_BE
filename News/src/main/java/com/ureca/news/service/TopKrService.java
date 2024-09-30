package com.ureca.news.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.openkoreantext.processor.OpenKoreanTextProcessorJava;
import org.openkoreantext.processor.tokenizer.KoreanTokenizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;

import com.ureca.news.dto.Articles;
import com.ureca.news.repository.ArticleRepository;

import reactor.core.publisher.Mono;
import scala.collection.JavaConverters;
import scala.collection.Seq;

@Service
public class TopKrService {
    @Value("${api-key}")
    private String API_KEY;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    private ArticleRepository articleRepository;

    public Mono<List<Map<String, Object>>> getTopNews(String category) {

        LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime yesterdayEnd = LocalDate.now().minusDays(1).atTime(23, 59, 59);

        List<Articles> storedArticles = articleRepository.findByCategoryAndPublishedAtBetween(category, yesterdayStart, yesterdayEnd);

        if (!storedArticles.isEmpty()) {
            return Mono.just(convertArticlesToMap(storedArticles));
        }

        String formattedDate = yesterdayEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String apiUrl = "https://api-v2.deepsearch.com/v1/articles/" + category +
                        "?date_from=" + formattedDate + "&date_to=" + formattedDate + "&size=200" + "&api_key=" + API_KEY;

        return webClientBuilder.build()
                .get()
                .uri(apiUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(responseMap -> {
                    List<Map<String, Object>> articleDataList = (List<Map<String, Object>>) responseMap.get("data");

                    if (articleDataList == null || articleDataList.isEmpty()) {
                        return Mono.just(Collections.emptyList());
                    }

                    List<String> nouns = extractNouns(articleDataList);

                    List<String> topNouns = getTopNouns(nouns, 5);

                    List<Map<String, Object>> articlesToSave = new ArrayList<>();
                    Set<String> savedTitles = new HashSet<>();

                    for (String noun : topNouns) {
                        Optional<Map<String, Object>> firstArticle = articleDataList.stream()
                                .filter(article -> article.get("title") != null && article.get("title").toString().contains(noun))
                                .filter(article -> !savedTitles.contains(article.get("title")))
                                .findFirst();

                        firstArticle.ifPresent(article -> {
                            saveArticle(article, category);
                            savedTitles.add(article.get("title").toString());
                            articlesToSave.add(convertMapToArticleMap(article));
                        });
                    }

                    return Mono.just(articlesToSave);
                });
    }

    private List<Map<String, Object>> convertArticlesToMap(List<Articles> articles) {
        return articles.stream().map(this::convertArticleToMap).collect(Collectors.toList());
    }

    private Map<String, Object> convertArticleToMap(Articles article) {
        Map<String, Object> articleMap = new HashMap<>();
        articleMap.put("title", article.getTitle());
        articleMap.put("publisher", article.getPublisher());
        articleMap.put("author", article.getAuthor());
        articleMap.put("imageUrl", article.getImageUrl());
        articleMap.put("publishedAt", article.getPublishedAt().toString());
        articleMap.put("contentUrl", article.getContentUrl());
        return articleMap;
    }

    private Map<String, Object> convertMapToArticleMap(Map<String, Object> articleData) {
        Map<String, Object> articleMap = new HashMap<>();
        articleMap.put("title", articleData.get("title"));
        articleMap.put("publisher", articleData.get("publisher"));
        articleMap.put("author", articleData.get("author"));
        articleMap.put("imageUrl", articleData.get("imageUrl"));
        articleMap.put("publishedAt", articleData.get("publishedAt"));
        articleMap.put("contentUrl", articleData.get("contentUrl"));
        return articleMap;
    }

    private void saveArticle(Map<String, Object> articleData, String category) {
        try {
            Articles article = new Articles();
            article.setTitle((String) articleData.get("title"));
            article.setPublisher((String) articleData.get("publisher"));
            article.setAuthor((String) articleData.get("author"));
            article.setImageUrl((String) articleData.get("image_url"));

            if (articleData.get("published_at") != null) {
                article.setPublishedAt(LocalDateTime.parse((String) articleData.get("published_at")));
            }
            
            article.setContentUrl((String) articleData.get("content_url"));
            article.setCategory(category);

            articleRepository.save(article);
        } catch (Exception e) {
            System.err.println("Error saving article: " + e.getMessage());
        }
    }
    private List<String> extractNouns(List<Map<String, Object>> articleDataList) {
        List<String> nouns = new ArrayList<>();
        Set<String> meaninglessWords = new HashSet<>(Arrays.asList(
            "뉴스", "뉴스스탠드", "오늘", "단독", "공개", "방송", "대응", "최고", "세계", "영화"
        ));

        Pattern hangulPattern = Pattern.compile("^[가-힣]+$");

        for (Map<String, Object> article : articleDataList) {
            String title = (String) article.get("title");
            String summary = (String) article.get("summary");

            if (title == null && summary == null) continue;

            String text = (title != null ? title : "") + " " + (summary != null ? summary : "");

            try {
                CharSequence normalized = OpenKoreanTextProcessorJava.normalize(text);
                Seq<KoreanTokenizer.KoreanToken> tokens = OpenKoreanTextProcessorJava.tokenize(normalized);

                List<KoreanTokenizer.KoreanToken> tokenList = JavaConverters.seqAsJavaList(tokens);

                for (KoreanTokenizer.KoreanToken token : tokenList) {
                    if (token.pos().toString().equals("Noun")) {
                        String noun = token.text();
                        if (noun.length() > 1 && hangulPattern.matcher(noun).matches() && !meaninglessWords.contains(noun)) {
                            nouns.add(noun);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing text: " + text + " Error: " + e.getMessage());
            }
        }

        return nouns;
    }
    
    private List<String> getTopNouns(List<String> nouns, int topN) {
        Map<String, Long> frequencyMap = nouns.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}