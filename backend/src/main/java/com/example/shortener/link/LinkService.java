package com.example.shortener.link;

import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LinkService {
    private final LinkRepository repository;
    private final UrlValidator validator;
    private final ShortCodeGenerator generator;
    private final LinkProperties properties;
    private final TransactionTemplate transaction;

    public LinkService(LinkRepository repository, UrlValidator validator, ShortCodeGenerator generator,
            LinkProperties properties, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.validator = validator;
        this.generator = generator;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CreateLinkResponse create(String originalUrl) {
        String url = validator.validate(originalUrl);
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = generator.generate();
            try {
                transaction.executeWithoutResult(status -> repository.saveAndFlush(new Link(url, code)));
                return new CreateLinkResponse(url, code, properties.shortUrl(code));
            } catch (DataAccessException | TransactionException exception) {
                if (!isCodeCollision(exception) || attempt == 4) {
                    throw new LinkUnavailableException(exception);
                }
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }

    private boolean isCodeCollision(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())
                    && sql.getMessage() != null && sql.getMessage().contains("uk_links_short_code")) {
                return true;
            }
        }
        return false;
    }
}
