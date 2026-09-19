package com.kkdev.waroracle.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer
{

    private final LogTagInterceptor logTagInterceptor;

    public WebMvcConfig(LogTagInterceptor logTagInterceptor)
    {
        this.logTagInterceptor = logTagInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(logTagInterceptor).addPathPatterns("/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry)
    {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
