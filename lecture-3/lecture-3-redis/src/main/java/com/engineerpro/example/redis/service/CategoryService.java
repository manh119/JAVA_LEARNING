package com.engineerpro.example.redis.service;

import java.io.IOException;

import org.springframework.stereotype.Service;

import com.engineerpro.example.redis.dto.GetCategoryArticlesRequest;
import com.engineerpro.example.redis.dto.GetCategoryArticlesResponse;
import com.engineerpro.example.redis.dto.GetCategoryResponse;
import com.engineerpro.example.redis.exception.CategoryNotFoundException;

@Service
public interface CategoryService {
  GetCategoryArticlesResponse getArticles(GetCategoryArticlesRequest request)
      throws CategoryNotFoundException, IOException;

  void rateLimitedGetArticlesByIP(GetCategoryArticlesRequest request)
      throws CategoryNotFoundException, IOException;

  void rateLimitedGetArticlesByApiKey(GetCategoryArticlesRequest request) throws CategoryNotFoundException, IOException;

  GetCategoryResponse getCategories() throws IOException;
}
