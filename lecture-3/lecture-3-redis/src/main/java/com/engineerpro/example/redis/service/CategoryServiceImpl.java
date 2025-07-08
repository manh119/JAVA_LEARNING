package com.engineerpro.example.redis.service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.engineerpro.example.redis.dto.GetCategoryArticlesRequest;
import com.engineerpro.example.redis.dto.GetCategoryArticlesResponse;
import com.engineerpro.example.redis.dto.GetCategoryResponse;
import com.engineerpro.example.redis.exception.CategoryNotFoundException;
import com.engineerpro.example.redis.model.Article;
import com.engineerpro.example.redis.model.Category;
import com.engineerpro.example.redis.repository.ArticleRepository;
import com.engineerpro.example.redis.repository.CategoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {
  private static final int CACHE_TIME_IN_MINUTE = 5;
  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private ArticleRepository articleRepository;

  @Autowired
  private RedisTemplate<String, String> redisTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public GetCategoryArticlesResponse getArticles(GetCategoryArticlesRequest request)
      throws IOException {

    if (redisTemplate.hasKey("category:" + request.getCategoryId())) {
      String cachedArticles = redisTemplate.opsForValue().get("category:" + request.getCategoryId());

      log.info("Cache hit for category id={}", request.getCategoryId());
      List<Article> articles = objectMapper.readValue(cachedArticles,
          objectMapper.getTypeFactory().constructCollectionType(List.class, Article.class));
      return GetCategoryArticlesResponse.builder()
          .articles(articles)
          .build();
    } else {
      log.info("Cache miss for category id={}", request.getCategoryId());
      List<Article> articles = articleRepository
          .findByCategoryId(request.getCategoryId());

      // Cache the articles for 5 minutes
      redisTemplate.opsForValue().set("category:" + request.getCategoryId(),
          objectMapper.writeValueAsString(articles),
          Duration.ofMinutes(CACHE_TIME_IN_MINUTE));
      return GetCategoryArticlesResponse.builder()
          .articles(articles)
          .build();
    }

  }

  @Override
  public void rateLimitedGetArticlesByIP(GetCategoryArticlesRequest request)
      throws CategoryNotFoundException, IOException {
    String ip = request.getIpAddress();
    String key = "rate_limit:" + ip;
    int limit = 5; // max 5 requests
    int windowSeconds = 60; // per minute

    Long count = redisTemplate.opsForValue().increment(key);
    if (count == 1) {
      redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
    }
    if (count > limit) {
      throw new IOException("Rate limit exceeded");
    }

  }

  @Override
  public void rateLimitedGetArticlesByApiKey(GetCategoryArticlesRequest request)
      throws CategoryNotFoundException, IOException {
    String apiKey = request.getApiKey();
    String key = "rate_limit:api_key:" + apiKey;
    int limit = 10; // max 10 requests
    int windowSeconds = 60; // per minute

    Long count = redisTemplate.opsForValue().increment(key);
    if (count == 1) {
      redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
    }
    if (count > limit) {
      throw new IOException("Rate limit exceeded");
    }
    // Optionally call getArticles(request) here if you want
  }

  @Override
  public GetCategoryResponse getCategories() throws IOException {
    return GetCategoryResponse.builder().categories(categoryRepository.findAll()).build();
  }

}
