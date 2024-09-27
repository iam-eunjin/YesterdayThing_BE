package com.ureca.news.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ureca.news.dto.News;

public interface NewsRepository extends JpaRepository<News, Long> {
    List<News> findByCategory(String category);

	boolean existsByUrl(String articleLink);

    List<News> findByCategoryAndPublishedAtBetween(String category, LocalDateTime start, LocalDateTime end);

}