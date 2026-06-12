package io.github.notablogger.springxpose.processor;

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
            import io.github.notablogger.springxpose.annotation.*;
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
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("BookRequestDto");
    }

    @Test
    void omittedOperationProducesNoMethod() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith(
                "@ExposeEntity(path=\"books\", expose={Operation.FIND_ALL, Operation.FIND_BY_ID})"),
                REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("findAll");
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("findById");
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("@GetMapping");
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
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController")
            .contentsAsUtf8String().contains("mapper.toEntity");
    }

    @Test
    void repositoryGeneratedInGeneratedPackage() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\")"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookRepository");
    }

    @Test
    void writeMethodsAreTransactional() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\")"), REPO);

        assertThat(c).succeeded();
        String src = c.generatedSourceFile("com.example.entity.generated.BookController")
            .orElseThrow().getCharContent(false).toString();
        org.junit.jupiter.api.Assertions.assertTrue(src.contains("@Transactional"),
            "Write methods must be annotated with @Transactional");
    }

    @Test
    void updateUsesLoadThenMerge() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\")"), REPO);

        assertThat(c).succeeded();
        String src = c.generatedSourceFile("com.example.entity.generated.BookController")
            .orElseThrow().getCharContent(false).toString();
        org.junit.jupiter.api.Assertions.assertTrue(src.contains("findById(id)"),
            "UPDATE must load the entity first (load-then-merge)");
        org.junit.jupiter.api.Assertions.assertTrue(src.contains("mapper.updateEntity"),
            "UPDATE must call mapper.updateEntity to merge fields without blind overwrite");
    }

    @Test
    void pageableTrueGeneratesPageableParameter() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\", pageable=true)"), REPO);

        assertThat(c).succeeded();
        String src = c.generatedSourceFile("com.example.entity.generated.BookController")
            .orElseThrow().getCharContent(false).toString();
        org.junit.jupiter.api.Assertions.assertTrue(src.contains("Pageable"),
            "pageable=true must generate a Pageable parameter on findAll");
        org.junit.jupiter.api.Assertions.assertTrue(src.contains("Page"),
            "pageable=true must return Page<Dto> not List<Dto>");
    }

    @Test
    void pageableFalseGeneratesFlatList() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\", pageable=false)"), REPO);

        assertThat(c).succeeded();
        String src = c.generatedSourceFile("com.example.entity.generated.BookController")
            .orElseThrow().getCharContent(false).toString();
        org.junit.jupiter.api.Assertions.assertFalse(src.contains("Pageable"),
            "pageable=false must NOT generate a Pageable parameter");
        org.junit.jupiter.api.Assertions.assertTrue(src.contains("List<"),
            "pageable=false must return List<Dto>");
    }

    // ── filterableFields compile-tests ────────────────────────────────────────

    @Test
    void filterableFieldsGeneratesFilterParamsClass() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\", filterableFields={\"title\"})"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookFilterParams");
    }

    @Test
    void filterableFieldsGeneratesSpecClass() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\", filterableFields={\"title\"})"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookSpec");
    }

    @Test
    void filterableFieldsGeneratesModelAttributeParamOnFindAll() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\", filterableFields={\"title\"})"), REPO);

        assertThat(c).succeeded();
        String src = c.generatedSourceFile("com.example.entity.generated.BookController")
            .orElseThrow().getCharContent(false).toString();
        org.junit.jupiter.api.Assertions.assertTrue(src.contains("@ModelAttribute"),
            "filterableFields must add @ModelAttribute FilterParams param to findAll");
        org.junit.jupiter.api.Assertions.assertTrue(src.contains("BookFilterParams"),
            "filterableFields must reference generated BookFilterParams in controller");
        org.junit.jupiter.api.Assertions.assertTrue(src.contains("BookSpec.withFilters"),
            "filterableFields must call BookSpec.withFilters in the findAll body");
    }

    @Test
    void filterableFieldsExtendsRepositoryWithJpaSpecificationExecutor() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\", filterableFields={\"title\"})"), REPO);

        assertThat(c).succeeded();
        String src = c.generatedSourceFile("com.example.entity.generated.BookRepository")
            .orElseThrow().getCharContent(false).toString();
        org.junit.jupiter.api.Assertions.assertTrue(src.contains("JpaSpecificationExecutor"),
            "Repository must extend JpaSpecificationExecutor when filterableFields declared");
    }

    @Test
    void noFilterableFieldsDoesNotExtendJpaSpecificationExecutor() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\")"), REPO);

        assertThat(c).succeeded();
        String src = c.generatedSourceFile("com.example.entity.generated.BookRepository")
            .orElseThrow().getCharContent(false).toString();
        org.junit.jupiter.api.Assertions.assertFalse(src.contains("JpaSpecificationExecutor"),
            "Repository must NOT extend JpaSpecificationExecutor when filterableFields is empty");
    }
}
