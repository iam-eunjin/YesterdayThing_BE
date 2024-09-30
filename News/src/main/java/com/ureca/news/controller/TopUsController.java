package com.ureca.news.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ureca.news.dto.News;
import com.ureca.news.repository.NewsRepository;
import com.ureca.news.service.TopUsService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/news")
@CrossOrigin(origins = "*")
public class TopUsController {
    private final TopUsService topUsService;

    @Autowired
    private NewsRepository newsRepository;

    public TopUsController(TopUsService topUsService) {
        this.topUsService = topUsService;
    }

    @GetMapping("/top")
    public Mono<List<Map<String, Object>>> getTopNews(@RequestParam(name = "category") String category) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime startOfDay = yesterday.atStartOfDay(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime endOfDay = yesterday.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toLocalDateTime();

        List<News> storedArticles = newsRepository.findByCategoryAndPublishedAtBetween(category, startOfDay, endOfDay);

        if (!storedArticles.isEmpty()) {

            List<Map<String, Object>> response = storedArticles.stream().map(article -> {
                Map<String, Object> articleMap = new HashMap<>();
                articleMap.put("title", article.getTitle());
                articleMap.put("url", article.getUrl());
                articleMap.put("urlToImage", article.getUrlToImage());
                articleMap.put("description", article.getDescription());
                articleMap.put("content", article.getContent());
                articleMap.put("publishedAt", article.getPublishedAt().toString());
                return articleMap;
            }).collect(Collectors.toList());
            return Mono.just(response);
        }
        return topUsService.getTopNews(category);
    }
}