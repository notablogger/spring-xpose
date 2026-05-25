package io.github.springxpose.sample;

import io.github.springxpose.sample.entity.Category;
import io.github.springxpose.sample.entity.generated.CategoryRepository;
import io.github.springxpose.sample.entity.Product;
import io.github.springxpose.sample.entity.generated.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public DataLoader(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) return; // idempotent

        Category electronics = new Category();
        electronics.setName("Electronics");
        categoryRepository.save(electronics);

        Category books = new Category();
        books.setName("Books");
        categoryRepository.save(books);

        Product laptop = new Product();
        laptop.setName("Laptop Pro 14");
        laptop.setPrice(1299.99);
        laptop.setDescription("High-performance laptop for developers");
        laptop.setCategory(electronics);
        productRepository.save(laptop);

        Product headphones = new Product();
        headphones.setName("Noise-Cancelling Headphones");
        headphones.setPrice(299.99);
        headphones.setDescription("Premium wireless headphones");
        headphones.setCategory(electronics);
        productRepository.save(headphones);

        Product book = new Product();
        book.setName("Effective Java");
        book.setPrice(49.99);
        book.setDescription("The definitive guide to Java programming");
        book.setCategory(books);
        productRepository.save(book);

        System.out.println("✅ DataLoader: seeded 2 categories and 3 products");
    }
}
