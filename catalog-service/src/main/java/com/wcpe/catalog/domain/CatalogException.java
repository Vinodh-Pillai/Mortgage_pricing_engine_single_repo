package com.wcpe.catalog.domain;

class CatalogException extends RuntimeException {
  CatalogException(String code) {
    super(code);
  }
}
