package dev.trooped.tvquickbars.data

/**
 * CategoryItem Data Class
 * Represents a category of entities in the EntityImporterActivity.
 * @property name The name of the category.
 * @property entities A list of EntityItem objects representing the entities in this category.
 * @property isExpanded A boolean indicating whether the category is expanded or not.
 */

data class CategoryItem(
    val name: String,
    var entities: List<EntityItem>,
    var isExpanded: Boolean = false,
    var matchCount: Int = 0
)