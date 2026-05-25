package io.github.notablogger.springxpose.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExposeEntityProcessorTest {

    private static final javax.tools.JavaFileObject BOOK_SOURCE = JavaFileObjects.forSourceString(
        "com.example.entity.Book",
        """
        package com.example.entity;

        import io.github.notablogger.springxpose.annotation.ExposeEntity;
        import jakarta.persistence.*;

        @Entity
        @ExposeEntity(path = "books")
        public class Book {

            @Id
            @GeneratedValue
            private Long id;

            private String title;

            private String author;

            public Long getId() { return id; }
            public void setId(Long id) { this.id = id; }
            public String getTitle() { return title; }
            public void setTitle(String title) { this.title = title; }
            public String getAuthor() { return author; }
            public void setAuthor(String author) { this.author = author; }
        }
        """
    );

    /** Stub repository so the generated controller can compile cleanly. */
    private static final javax.tools.JavaFileObject BOOK_REPO_SOURCE = JavaFileObjects.forSourceString(
        "com.example.entity.BookRepository",
        """
        package com.example.entity;

        import org.springframework.data.jpa.repository.JpaRepository;

        public interface BookRepository extends JpaRepository<Book, Long> {}
        """
    );

    @Test
    void generatesControllerForSimpleEntity() {
        Compilation compilation = javac()
            .withProcessors(new ExposeEntityProcessor())
            .compile(BOOK_SOURCE, BOOK_REPO_SOURCE);

        compilation.diagnostics().forEach(d ->
            System.out.println("[DIAG] " + d.getKind() + ": " + d.getMessage(null)));
        compilation.generatedSourceFiles().forEach(f ->
            System.out.println("[GEN] " + f.getName()));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation).generatedSourceFile("com.example.entity.generated.BookController");
        assertThat(compilation).generatedSourceFile("com.example.entity.generated.BookSecurityConfigurer");
    }

    @Test
    void failsWhenNoIdField() {
        Compilation compilation = javac()
            .withProcessors(new ExposeEntityProcessor())
            .compile(JavaFileObjects.forSourceString(
                "com.example.entity.NoIdEntity",
                """
                package com.example.entity;

                import io.github.notablogger.springxpose.annotation.ExposeEntity;
                import jakarta.persistence.*;

                @Entity
                @ExposeEntity
                public class NoIdEntity {
                    private String name;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
                """
            ));

        compilation.diagnostics().forEach(d ->
            System.out.println("[DIAG] " + d.getKind() + ": " + d.getMessage(null)));

        // Use manual checks — compile-testing's CompilationSubject.failed() internally uses
        // JUnit 4's Assert.fail() which throws IllegalStateException under JUnit 5.
        assertFalse(
            compilation.status() == Compilation.Status.SUCCESS,
            "Compilation should have failed but succeeded"
        );
        boolean hasExpectedError = compilation.diagnostics().stream()
            .anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
                && d.getMessage(null).contains("@ExposeEntity: no @Id field found"));
        assertTrue(hasExpectedError, "Expected error '@ExposeEntity: no @Id field found' not found in diagnostics");
    }
}
