package com.ureca.news.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.google.gson.Gson;

@RestController
@CrossOrigin(origins = "*")
public class NaverController {
	@Value("${X-Naver-Client-Id}")
	private String Id;

	@Value("${X-Naver-Client-Secret}")
	private String Secret;

	class Information {
	    public String lastBuildDate;
	    public int total;
	    public int start;
	    public int display;
	    public List<Item> items;
	}

	class Item {
	    public String title;
	    public String originalLink;
	    public String link;
	    public String description;
	    public String pubDate;

	    @Override
	    public String toString() {
	        return "Item{" +
	                "title='" + title + '\'' +
	                ", originalLink='" + originalLink + '\'' +
	                ", link='" + link + '\'' +
	                ", description='" + description + '\'' +
	                ", pubDate='" + pubDate + '\'' +
	                '}';
	    }
	}

	@GetMapping("/news")
	public ResponseEntity<Information> naver() {
	    String query = "환율";
	    String encode = UriUtils.encode(query, StandardCharsets.UTF_8);

	    URI uri = UriComponentsBuilder
	            .fromUriString("https://openapi.naver.com")
	            .path("/v1/search/news.json")
	            .queryParam("query", encode)
	            .queryParam("display", 10)
	            .queryParam("start", 1)
	            .encode()
	            .build()
	            .toUri();

        RestTemplate restTemplate = new RestTemplate();

        RequestEntity<Void> req = RequestEntity
                .get(uri)
                .header("X-Naver-Client-Id", Id)
                .header("X-Naver-Client-Secret", Secret)
                .build();

        ResponseEntity<String> result = restTemplate.exchange(req, String.class);
        Gson gson = new Gson();
        Information info = gson.fromJson(result.getBody(), Information.class);

        return ResponseEntity.ok(info);
    }
}