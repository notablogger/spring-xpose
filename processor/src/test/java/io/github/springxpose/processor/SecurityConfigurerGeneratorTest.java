package io.github.springxpose.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigurerGeneratorTest {

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
    void authTypeNone_generatesPermitAll() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\", authType=AuthType.NONE)"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookSecurityConfigurer")
            .contentsAsUtf8String().contains("permitAll");
    }

    @Test
    void authTypeBasic_generatesHttpBasic() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\", authType=AuthType.BASIC)"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookSecurityConfigurer")
            .contentsAsUtf8String().contains("httpBasic");
    }

    @Test
    void authTypeOauth2_generatesJwtConfig() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith("@ExposeEntity(path=\"books\", authType=AuthType.OAUTH2)"), REPO);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookSecurityConfigurer")
            .contentsAsUtf8String().contains("oauth2ResourceServer");
    }

    @Test
    void readAndWriteRoles_generatedInConfig() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(bookWith(
                "@ExposeEntity(path=\"books\", authType=AuthType.BASIC, readRoles={\"USER\"}, writeRoles={\"ADMIN\"})"),
                REPO);

        assertThat(c).succeeded();
        // Read the generated source file content directly
        String src = c.generatedSourceFile("com.example.entity.generated.BookSecurityConfigurer")
            .orElseThrow(() -> new AssertionError("BookSecurityConfigurer not generated"))
            .getCharContent(false)
            .toString();
        assertTrue(src.contains("USER") || src.contains("hasAnyRole"),
            "Expected USER role or hasAnyRole in: " + src);
        assertTrue(src.contains("ADMIN") || src.contains("hasAnyRole"),
            "Expected ADMIN role or hasAnyRole in: " + src);
    }
}
