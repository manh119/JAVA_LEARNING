package com.engineerpro.example.redis.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.engineerpro.example.redis.dto.GetCategoryArticlesRequest;
import com.engineerpro.example.redis.dto.GetCategoryArticlesResponse;
import com.engineerpro.example.redis.dto.GetCategoryResponse;
import com.engineerpro.example.redis.exception.CategoryNotFoundException;
import com.engineerpro.example.redis.service.CategoryService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(path = "/category")
public class Controller {
  @Autowired
  private CategoryService categoryService;

  @GetMapping()
  public ResponseEntity<GetCategoryResponse> getCategories() throws IOException {
    GetCategoryResponse response = categoryService.getCategories();
    return ResponseEntity.ok(response);
  }

  // @GetMapping("{id}/articles")
  // public ResponseEntity<GetCategoryArticlesResponse> getArticles(@PathVariable
  // Integer id) throws IOException {
  // log.info("request id={}", id);
  // GetCategoryArticlesResponse response;
  // try {
  // response =
  // categoryService.getArticles(GetCategoryArticlesRequest.builder().categoryId(id).build());
  // } catch (CategoryNotFoundException e) {
  // return ResponseEntity.badRequest().build();
  // }
  // return ResponseEntity.ok(response);
  // } // ...existing code...

  @GetMapping("{id}/articles")
  public ResponseEntity<GetCategoryArticlesResponse> getArticlesRateLimited(
      @PathVariable Integer id,
      HttpServletRequest servletRequest) throws IOException {
    String apiKey = servletRequest.getHeader("X-API-KEY");
    if (apiKey == null || apiKey.isEmpty()) {
      return ResponseEntity.status(401).build(); // Unauthorized if no API key
    }
    log.info("rate-limited request id={}, apiKey={}", id, apiKey);
    log.info("rate-limited request id={}", id);
    try {
      GetCategoryArticlesRequest req = GetCategoryArticlesRequest.builder()
          .categoryId(id)
          .apiKey(apiKey)
          .IpAddress(servletRequest.getRemoteAddr())
          .build();
      categoryService.rateLimitedGetArticlesByApiKey(req);
      categoryService.rateLimitedGetArticlesByIP(req);
      GetCategoryArticlesResponse response = categoryService.getArticles(req);
      return ResponseEntity.ok(response);
    } catch (CategoryNotFoundException e) {
      return ResponseEntity.badRequest().build();
    } catch (IOException e) {
      return ResponseEntity.status(429).build(); // 429 Too Many Requests
    }
  }
}
