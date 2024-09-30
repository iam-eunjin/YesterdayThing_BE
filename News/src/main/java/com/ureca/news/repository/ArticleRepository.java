package com.ureca.news.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ureca.news.dto.Articles;

public interface ArticleRepository extends JpaRepository<Articles, Long> {

	List<Articles> findByCategoryAndPublishedAtBetween(String category, LocalDateTime start, LocalDateTime end);
}