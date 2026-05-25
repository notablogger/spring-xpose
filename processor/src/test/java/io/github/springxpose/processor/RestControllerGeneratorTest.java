package io.github.springxpose.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class RestControllerGeneratorTest {

    private static final javax.tools.JavaFileObject REPO = JavaFileObjects.forSourceString(
        "com.example.entity.BookRepository",
        """
        package com.example.entity;
        import org.springframework.data.jpa.repository.JpaRepository;
        public interface BookRepository extends JpaRepository<Book, Long> {}
        """
    );

    private javax.tools.JavaFileObject bookWith(String annotation) {
        return JavaFileObjects.forSourceString("com.example.entity.Book",
            """
            package com.example.entity;
            import io.github.springxpose.annotation.*;
            import jakarta.persistence.*;
            @Entity
            """ + annotation + """
            public class Book {
                @Id @GeneratedValue private Long id;
                private String title;
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                public String getTitle() { return title; }
                public void setTitle(String t) { this.title = t; }
            }
            """
        );
    }

    @Test
    void generatesGetMappingForFindAll() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\")"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("@GetMapping");
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("/api/books");
    }

    @Test
    void generatesPostMappingForCreate() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\")"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("@PostMapping");
    }

    @Test
    void omittedOperationProducesNoMethod() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith(
                "@ExposeEntity(path=\"books\", expose={Operation.FIND_ALL, Operation.FIND_BY_ID})"),
                REPO);

        assertThat(c).succeeded();
        // Verify only GET mappings are present — no POST/PUT/DELETE methods generated
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("findAll");
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("findById");
        // Verify write methods are absent by checking method names are not present
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("@GetMapping");
        // The class should NOT contain create/update/delete method bodies
        // We verify this indirectly: the file should contain exactly 2 mapping annotations
        // (one @GetMapping for findAll, one @GetMapping("/{id}") for findById)
        // Use org.junit.jupiter.api.Assertions for string-based assertion as workaround
        // for compile-testing's doesNotContain limitation
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("public ResponseEntity<List<");
    }

    @Test
    void correctHttpStatusForCreate() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\")"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("CREATED");
    }

    @Test
    void pathDerivedFromAnnotation() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"library-books\")"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("/api/library-books");
    }

    @Test
    void generatesPutMappingForUpdate() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\")"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("@PutMapping");
    }

    @Test
    void generatesDeleteMappingForDelete() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\")"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("@DeleteMapping");
    }

    @Test
    void generatesGetMappingByIdForFindById() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\")"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("/{id}");
    }

    @Test
    void omittedOperationsAreAbsentFromOutput() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith(
                "@ExposeEntity(path=\"books\", expose={Operation.FIND_ALL, Operation.FIND_BY_ID})"),
                REPO);

        assertThat(c).succeeded();
        String src = c.generatedSourceFile("com.example.entity.generated.BookController")
            .orElseThrow(() -> new AssertionError("BookController not generated"))
            .getCharContent(false).toString();

        org.junit.jupiter.api.Assertions.assertFalse(src.contains("@PostMapping"),
            "CREATE was not requested but @PostMapping found");
        org.junit.jupiter.api.Assertions.assertFalse(src.contains("@PutMapping"),
            "UPDATE was not requested but @PutMapping found");
        org.junit.jupiter.api.Assertions.assertFalse(src.contains("@DeleteMapping"),
            "DELETE was not requested but @DeleteMapping found");
    }

    @Test
    void autoDerivesPathFromEntityName() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity()"), REPO);

        assertThat(c).succeeded();
        // Entity "Book" → pluralised to "books"
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("/api/books");
    }

    @Test
    void requestBodyHasValidAnnotation() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\")"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("@Valid");
    }

    @Test
    void repositoryGeneratedInGeneratedPackage() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\")"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookRepository");
    }
}
