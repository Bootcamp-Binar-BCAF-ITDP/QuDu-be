package com.delvin.loan.configuration;

import com.delvin.loan.common.DocumentRules;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class MultipartConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement() {

        MultipartConfigFactory factory = new MultipartConfigFactory();

        factory.setMaxFileSize(DataSize.ofBytes(DocumentRules.MAX_FILE_BYTES));
        factory.setMaxRequestSize(DataSize.ofBytes(DocumentRules.MAX_REQUEST_BYTES));

        return factory.createMultipartConfig();
    }
}
