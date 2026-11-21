package com.careermatcher.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/** Serializes Page results as a stable DTO ({content, page{size,number,totalElements,totalPages}}). */
@Configuration(proxyBeanMethods = false)
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WebDataConfig {}
