package org.example.yogabusinessmanagementweb.service.Impl;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.example.yogabusinessmanagementweb.common.Enum.EStatus;
import org.example.yogabusinessmanagementweb.common.entities.Category;
import org.example.yogabusinessmanagementweb.common.entities.Product;
import org.example.yogabusinessmanagementweb.common.entities.SubCategory;
import org.example.yogabusinessmanagementweb.common.mapper.CategoryMapper;
import org.example.yogabusinessmanagementweb.common.mapper.Mappers;
import org.example.yogabusinessmanagementweb.dto.request.category.CategoryCreationRequest;
import org.example.yogabusinessmanagementweb.dto.response.category.CategoryResponse;
import org.example.yogabusinessmanagementweb.dto.response.category.CategoryResponseAndQuantityProduct;
import org.example.yogabusinessmanagementweb.dto.response.category.CategoryWithProductResponse;
import org.example.yogabusinessmanagementweb.exception.AppException;
import org.example.yogabusinessmanagementweb.exception.ErrorCode;
import org.example.yogabusinessmanagementweb.repositories.CategoryRepository;
import org.example.yogabusinessmanagementweb.repositories.SubCategoryRepository;
import org.example.yogabusinessmanagementweb.service.CategoryService;
import org.example.yogabusinessmanagementweb.service.SubCategoryService;
import org.hibernate.validator.internal.util.stereotypes.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
@Service
public class CategoryServiceImpl implements CategoryService {

    CategoryRepository categoryRepository;
    CategoryMapper categoryMapper;
    SubCategoryRepository subCategoryRepository;

    @Override
    public CategoryResponse addCategory(CategoryCreationRequest categoryCreationRequest) {
        if(categoryCreationRequest.getUrlImage()==null){
            categoryCreationRequest.setUrlImage("https://bizweb.dktcdn.net/100/262/937/themes/813962/assets/collection_4_banner.jpg?1730181507139");
        }
        
        Category category = Mappers.convertToEntity(categoryCreationRequest, Category.class);


        // Gán giá trị mặc định cho status nếu nó là null
        if (category.getStatus() == null) {
            category.setStatus(EStatus.ACTIVE);
        }
        // Kiểm tra nếu tên đã tồn tại và status là true
        Optional<Category> existingCategory = categoryRepository.findByNameAndStatus(category.getName(), category.getStatus());

        if (existingCategory.isPresent()) {
            throw new AppException(ErrorCode.CATEGORY_EXISTS);
        }

        category = categoryRepository.save(category);

        CategoryResponse categoryResponse = Mappers.convertToDto(category, CategoryResponse.class);
        return categoryResponse;
    }

    @Override
    public Category findByIdAndStatus(Long id, EStatus status) {
        // Tìm kiếm danh mục theo tên và trạng thái
        return categoryRepository.findByIdAndStatus(id, status)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    @Override
    public List<CategoryResponse> getAllCategory() {
        // Lấy danh sách các Category có trạng thái ACTIVE
        List<Category> categories = categoryRepository.getAllByStatus(EStatus.ACTIVE);

        // Lọc danh sách categories và subcategories không hợp lệ
        return categories.stream()
                .filter(category -> category.getStatus() == EStatus.ACTIVE)  // Lọc category ACTIVE
                .map(category -> {
                    // Lọc các SubCategory trong Category
                    List<SubCategory> filteredSubCategories = category.getSubCategories().stream()
                            .filter(subCategory -> subCategory.getStatus() == EStatus.ACTIVE)  // Lọc subcategory ACTIVE
                            .collect(Collectors.toList());
                    category.setSubCategories(filteredSubCategories);  // Cập nhật lại list subcategories
                    return category;
                })
                .filter(category -> !category.getSubCategories().isEmpty())  // Lọc các category không có subcategory hợp lệ
                .map(categoryMapper::toCategoryResponse)  // Ánh xạ thành CategoryResponse (tương tự như CategoryWithProductResponse)
                .collect(Collectors.toList());
    }

//    @Override
//    public List<CategoryResponse> getAllCategory() {
//        List<CategoryResponse> list = new ArrayList<>();
//        List<Category> categoryList = categoryRepository.getAllByStatus(EStatus.ACTIVE);
//        for(Category category : categoryList){
//            CategoryResponse categoryResponse = categoryMapper.toCategoryResponse(category);
//            list.add(categoryResponse);
//        }
//        return list;
//    }
    @Override
    public List<CategoryResponse> getAllCategoryAdmin() {
        List<CategoryResponse> categoryResponses = new ArrayList<>();
        // Lấy danh sách các Category có trạng thái ACTIVE
        List<Category> categories = categoryRepository.getAllByStatus(EStatus.ACTIVE);
        for (Category category : categories) {
            CategoryResponse categoryResponse =  categoryMapper.toCategoryResponse(category);
            categoryResponses.add(categoryResponse);
        }
        return categoryResponses;
    }
    public List<CategoryWithProductResponse> getCategoriesWithProducts() {
        // Lấy danh sách các Category có trạng thái ACTIVE
        List<Category> categories = categoryRepository.getAllByStatus(EStatus.ACTIVE);

        // Lọc danh sách categories, subcategories và products không hợp lệ
        return categories.stream()
                .filter(category -> category.getStatus() == EStatus.ACTIVE)  // Lọc category ACTIVE
                .map(category -> {
                    // Lọc các SubCategory trong Category
                    List<SubCategory> filteredSubCategories = category.getSubCategories().stream()
                            .filter(subCategory -> subCategory.getStatus() == EStatus.ACTIVE)  // Lọc subcategory ACTIVE
                            .map(subCategory -> {
                                // Lọc các Product trong SubCategory
                                List<Product> filteredProducts = subCategory.getProducts().stream()
                                        .filter(product -> product.getStatus() == true)  // Lọc product có status TRUE
                                        .collect(Collectors.toList());
                                subCategory.setProducts(filteredProducts);  // Cập nhật lại list products
                                return subCategory;
                            })
                            .collect(Collectors.toList());
                    category.setSubCategories(filteredSubCategories);  // Cập nhật lại list subcategories
                    return category;
                })
                .filter(category -> !category.getSubCategories().isEmpty())  // Lọc các category không có subcategory hợp lệ
                .map(categoryMapper::toCategoryWithProductResponse)  // Ánh xạ thành CategoryWithProductResponse
                .collect(Collectors.toList());
    }


    @Override
    public List<CategoryResponseAndQuantityProduct> getAllCategoryAndQuantityProduct() {
        List<CategoryResponseAndQuantityProduct> list = new ArrayList<>();
        CategoryResponseAndQuantityProduct categoryResponseAndQuantityProduct = new CategoryResponseAndQuantityProduct();

        List<Category> categoryList = categoryRepository.findAll();

        for(Category category : categoryList){
            categoryResponseAndQuantityProduct = Mappers.convertToDto(category, CategoryResponseAndQuantityProduct.class);


            int quantity = 0;
            for(SubCategory subCategory : category.getSubCategories()){
                quantity+= subCategory.getProducts().size();
                categoryResponseAndQuantityProduct.setQuantity(quantity);
            }

            list.add(categoryResponseAndQuantityProduct);
        }
        return list;
    }

    public SubCategory getSubCategoryById(String id) {
        Optional<SubCategory> subCategoryOptional = subCategoryRepository.findById(Long.valueOf(id));
        if(subCategoryOptional.isEmpty()) {
            throw  new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND);
        }
        return subCategoryOptional.get();
    }
    public void deleteSubCategoryWithStatus(String id,boolean status,EStatus statusSub) {
        SubCategory sub =  getSubCategoryById(id);
        sub.setStatus(statusSub);
        for(Product product : sub.getProducts()) {
            product.setStatus(status);
        }
        subCategoryRepository.save(sub);
    }

    @Override
    public void changeCategoryWithStatus(String id) {
        Optional<Category> categoryOptional  =  categoryRepository.findById(Long.parseLong(id));
        if(categoryOptional.isEmpty()){
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        Category category = categoryOptional.get();
        if(category.getStatus() == EStatus.ACTIVE){
            category.setStatus(EStatus.INACTIVE);

            for(SubCategory subCategory : category.getSubCategories()){
//                subCategory.setStatus(EStatus.INACTIVE);
                deleteSubCategoryWithStatus(String.valueOf(subCategory.getId()),false,EStatus.INACTIVE);
            }
        }else {
            category.setStatus(EStatus.ACTIVE);

            for(SubCategory subCategory : category.getSubCategories()){
//                subCategory.setStatus(EStatus.ACTIVE);
                deleteSubCategoryWithStatus(String.valueOf(subCategory.getId()),true,EStatus.ACTIVE);
            }
        }
        categoryRepository.save(category);
    }
}

