package com.relix.marketplace.worker.repository;

import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVariant;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.Credential;
import com.relix.marketplace.worker.entity.WorkerProfile;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CredentialRepositoryDataJpaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("credential_repository_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void pessimisticLookupWorksWithOptionalDocumentAndReviewerAssociations() {
        User workerUser = User.builder()
                .email("credential-repository@example.com")
                .passwordHash("test-hash")
                .name("Credential Repository Worker")
                .build();
        entityManager.persist(workerUser);

        WorkerProfile worker = WorkerProfile.builder()
                .user(workerUser)
                .displayName("Credential Repository Worker")
                .build();
        entityManager.persist(worker);

        Credential credential = Credential.builder()
                .worker(worker)
                .type(Credential.Type.DRIVER_LICENSE)
                .status(Credential.Status.PENDING)
                .build();
        entityManager.persist(credential);
        entityManager.flush();

        StoredFile document = StoredFile.builder()
                .storageKey("credential-repository-document.jpg")
                .variant(FileVariant.LARGE)
                .visibility(FileVisibility.PRIVATE)
                .ownerType(FileOwnerType.CREDENTIAL_DOCUMENT)
                .ownerId(credential.getId())
                .uploader(workerUser)
                .contentType("image/jpeg")
                .byteSize(2_048L)
                .width(1_000)
                .height(750)
                .build();
        entityManager.persist(document);
        credential.setDocumentFile(document);
        entityManager.flush();
        entityManager.clear();

        Credential locked = credentialRepository.findByIdForUpdate(credential.getId())
                .orElseThrow();

        assertThat(locked.getWorker().getUser().getId()).isEqualTo(workerUser.getId());
        assertThat(locked.getDocumentFile().getId()).isEqualTo(document.getId());
        assertThat(locked.getReviewedBy()).isNull();
    }
}
