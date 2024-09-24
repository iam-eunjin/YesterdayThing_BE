package com.ureca.news.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ureca.news.service.TopUsService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/news")
@CrossOrigin(origins = "*")
public class TopUsController {
    private final TopUsService topUsService;

    public TopUsController(TopUsService topUsService) {
        this.topUsService = topUsService;
    }

    @GetMapping("/top")
    public Mono<List<Map<String, Object>>> getTopNews(@RequestParam(name = "category") String category) {
        return topUsService.getTopNews(category);
    }
}