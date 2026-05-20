package com.goosepl.coastCalculator.domain.recipe;

import com.goosepl.coastCalculator.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "recipes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private int servings;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordering ASC")
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    private Recipe(User user, String name, int servings) {
        this.user = user;
        this.name = name;
        this.servings = servings;
    }

    public void update(String name, int servings) {
        this.name = name;
        this.servings = servings;
    }

    public void addIngredient(RecipeIngredient ingredient) {
        ingredient.assignRecipe(this);
        ingredients.add(ingredient);
    }

    public void clearIngredients() {
        ingredients.clear();
    }

    public boolean isOwnedBy(String username) {
        return user != null && user.getUsername().equals(username);
    }
}
