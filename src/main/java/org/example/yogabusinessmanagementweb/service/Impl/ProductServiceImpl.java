package org.example.yogabusinessmanagementweb.service.Impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.yogabusinessmanagementweb.common.entities.Notification;
import org.example.yogabusinessmanagementweb.common.entities.User;
import org.example.yogabusinessmanagementweb.common.mapper.GenericMapper;
import org.example.yogabusinessmanagementweb.common.mapper.ProductMapper;
import org.example.yogabusinessmanagementweb.dto.request.product.ProductCreationRequest;
import org.example.yogabusinessmanagementweb.dto.response.ListDto;
import org.example.yogabusinessmanagementweb.dto.response.product.ProductResponse;
import org.example.yogabusinessmanagementweb.exception.AppException;
import org.example.yogabusinessmanagementweb.exception.ErrorCode;
import org.example.yogabusinessmanagementweb.repositories.NotificationRepository;
import org.example.yogabusinessmanagementweb.repositories.ProductRepository;
import org.example.yogabusinessmanagementweb.repositories.SubCategoryRepository;
import org.example.yogabusinessmanagementweb.service.ProductService;
import org.example.yogabusinessmanagementweb.common.entities.Product;
import org.example.yogabusinessmanagementweb.common.entities.SubCategory;
import org.example.yogabusinessmanagementweb.service.SubCategoryService;
import org.example.yogabusinessmanagementweb.service.UserService;
import org.example.yogabusinessmanagementweb.utils.RanDomCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class ProductServiceImpl implements ProductService {
    ProductRepository productRepository;
    SubCategoryRepository subCategoryRepository;
    ProductMapper productMapper;
    SubCategoryService subCategoryService;
    UserService userService;
    NotificationRepository notificationRepository;
    SimpMessagingTemplate messagingTemplate;

    RanDomCode ranDomCode;


    @Override
    public Page<Product> getAllProduct(Pageable pageable) {
        return productRepository.findAll(pageable);
    }
    @Override
    public Product getProductById(String id) {
        return productRepository.findProductById(Long.valueOf(id))
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }
    @Override
    public ProductResponse getById(String id) {
        return productMapper.toProductResponse((getProductById(id)));
    }
    @Override
    public Page<ProductResponse> searchProducts(Boolean status,String keyword, Pageable pageable) {
        Page<Product> productPage;

        if (keyword == null || keyword.isEmpty()) {
            productPage = productRepository.findProductsWithStatus(status,pageable); // Lấy tất cả sản phẩm theo status
        } else {
            productPage = productRepository.findByTitleContainingIgnoreCase(keyword, pageable); // Tìm kiếm theo từ khóa
        }

        // Chuyển từ Page<Product> sang Page<ProductResponse>
        List<ProductResponse> productResponses =productMapper.productsToProductResponses(productPage.getContent());

        return new PageImpl<>(productResponses, pageable, productPage.getTotalElements());
    }
    public List<Product> getTop10BestSellingProducts() {
        return productRepository.findTop10BestSellingProducts();
    }

    @Override
    public void changeProductWithStatus(String productId) {
        Optional<Product> product = productRepository.findById(Long.valueOf(productId));
        if (product.isEmpty()) {
           throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        Product productEntity = product.get();
        if(productEntity.getStatus()==true){
            productEntity.setStatus(false);
        }else {
            productEntity.setStatus(true);
        }

        productRepository.save(productEntity);
    }

    @Override
    public Page<ProductResponse> getAllProductBySubcategory(String id,String keyword, Pageable pageable) {
        SubCategory subCategory =  subCategoryService.getSubCategoryById(id);
        Page<Product> productPage;

        if (keyword == null || keyword.isEmpty()) {
            productPage = productRepository.findBySubCategory(subCategory,pageable); // Lấy tất cả sản phẩm
        } else {
            productPage = productRepository.findByTitleContainingIgnoreCase(keyword, pageable); // Tìm kiếm theo từ khóa
        }

        // Chuyển từ Page<Product> sang Page<ProductResponse>
        List<ProductResponse> productResponses =productMapper.productsToProductResponses(productPage.getContent());

        return new PageImpl<>(productResponses, pageable, productPage.getTotalElements());
    }


    @Override
    public Product addProduct(ProductCreationRequest productCreationRequest)  {
        Product product = productMapper.toProduct(productCreationRequest);
        //xử lý SubCategory
        SubCategory subCategory = subCategoryRepository.findById(productCreationRequest.getSubCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));

        product.setSubCategory(subCategory);


        List<User> list = userService.getAllUser();
        for(User user : list){
            Notification notification = new Notification();
            notification.setTitle("Thêm sản phẩm mới");
            notification.setMessage("Sản phẩm" +" "+product.getTitle()+" "+"vừa được thêm vào hệ thống");
            notification.setRead(false);
            notification.setUser(user);
            notificationRepository.save(notification);
        }

        //Trạng thai product
        product.setStatus(true);

        //set code product
        String uniqueCode =  ranDomCode.generateUniqueCode();
        product.setCode(uniqueCode);
        //Lưu product
        return productRepository.save(product);
    }
    @Transactional
    @Override
    public void importProductsByExcel(MultipartFile file) {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0); // đọc sheet đầu tiên

            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue; // Bỏ qua header
                }

                String code = getCellStringValue(row.getCell(0)); // Mã sản phẩm
                String title = getCellStringValue(row.getCell(1)); // Tên sản phẩm
                double price = getCellNumericValue(row.getCell(2)); // Giá bán
                String description = getCellStringValue(row.getCell(3)); // Mô tả
                double quantity = getCellNumericValue(row.getCell(4)); // Số lượng
                String createdAtStr = getCellStringValue(row.getCell(5)); // Ngày tạo


// Nếu thiếu SubCategoryId trong file => tự gán cứng
                Long subCategoryId = 1L; // Hoặc lookup theo loại sản phẩm nếu có thêm thông tin

                ProductCreationRequest request = new ProductCreationRequest();
                request.setTitle(title);
                request.setPrice(BigDecimal.valueOf(price));
                request.setBrand("No brand"); // file không có brand, có thể gán tạm
                request.setDescription(description);
                request.setSubCategoryId(subCategoryId);
                request.setImagePath("default.jpg"); // hoặc để trống

                addProduct(request);
            }
        } catch (IOException e) {
//            throw new AppException(ErrorCode.EXCEL_IMPORT_ERROR, "Error reading Excel file", e);
        }
    }


    @Override
    public boolean delete(String id) {
        try {
            Optional<Product> product = productRepository.findById(Long.valueOf(id));
            if (product.isPresent()) {
                Product productEntity = product.get();
                productRepository.deleteById(productEntity.getId());
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    @Override
    public ListDto<List<ProductResponse>> filterProducts(Long subCategoryId, Long categoryId, String keyword, Pageable pageable) {
        Page<Product> productPage = productRepository.filterProducts(subCategoryId, categoryId, keyword,true, pageable);

        // Map các sản phẩm sang ProductResponse
        List<ProductResponse> productResponses = productMapper.productsToProductResponses(productPage.getContent());

        // Chuyển đổi sang ListDto
        return GenericMapper.toListDto(productResponses, productPage);
    }

    @Override
    public void updateProduct(Product product,Double rating,Double sold) {
        product.setAverageRating(rating);
        product.setSold(sold);
        productRepository.save(product);
    }
    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue()); // nếu là số thì convert về String
        }
        return "";
    }

    private double getCellNumericValue(Cell cell) {
        if (cell == null) return 0.0;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                return Double.parseDouble(cell.getStringCellValue().trim());
            } catch (NumberFormatException e) {
                return 0.0; // hoặc throw exception tùy bạn muốn strict hay không
            }
        }
        return 0.0;
    }
}
