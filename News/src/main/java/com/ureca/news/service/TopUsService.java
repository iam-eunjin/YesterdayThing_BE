package com.ureca.news.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;

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

    // 불용어 리스트
    private static final Set<String> stopWords = Set.of(
        "the", "is", "at", "of", "on", "and", "a", "an", "in", "to", "for", "by", "with", "about", "as", "it", "that"
    );

    public Mono<List<Map<String, Object>>> getTopNews(@RequestParam String category) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedDate = yesterday.format(formatter);

        String categoryUrl = "https://newsapi.org/v2/top-headlines?country=us&category=" + category
                + "&from=" + formattedDate + "&to=" + formattedDate + "&pageSize=100&apiKey=" + API_KEY;

        return webClientBuilder.build()
                .get()
                .uri(categoryUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (List<Map<String, Object>>) response.get("articles"))
                .flatMap(this::extractTopNouns)
                .doOnError(error -> System.err.println("Error: " + error.getMessage()));
    }


    private Mono<List<Map<String, Object>>> extractTopNouns(List<Map<String, Object>> articles) {
        Map<String, Integer> nounCount = new HashMap<>();
        List<Map<String, Object>> selectedArticles = new ArrayList<>();

        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma");
        props.setProperty("outputFormat", "json");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        for (Map<String, Object> article : articles) {
            String title = (String) article.get("title");

            if (title.contains(" - ")) {
                title = title.substring(0, title.lastIndexOf(" - ")).trim();
            }

            Annotation annotation = new Annotation(title);
            pipeline.annotate(annotation);

            List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
            for (CoreMap sentence : sentences) {
                for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                    String word = token.word().toLowerCase();
                    String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class); // 품사 태그

                    if ((pos.startsWith("NN") || pos.startsWith("JJ")) && !stopWords.contains(word)) {
                        nounCount.put(word, nounCount.getOrDefault(word, 0) + 1);
                    }
                }
            }
        }

        List<Map.Entry<String, Integer>> sortedNouns = new ArrayList<>(nounCount.entrySet());
        sortedNouns.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        Set<String> usedLinks = new HashSet<>();
        for (int i = 0; i < Math.min(5, sortedNouns.size()); i++) {
            String topNoun = sortedNouns.get(i).getKey();
            for (Map<String, Object> article : articles) {
                String articleTitle = (String) article.get("title");
                String articleLink = (String) article.get("url");
                if (articleTitle.toLowerCase().contains(topNoun) && !usedLinks.contains(articleLink)) {
                    selectedArticles.add(article);
                    usedLinks.add(articleLink);
                    break;
                }
            }
        }
        return Mono.just(selectedArticles);
    }
}