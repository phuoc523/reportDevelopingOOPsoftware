SET NAMES utf8mb4;

INSERT INTO products
    (id, tenant_id, name, description, fragrance_notes, image_url, default_price, available, created_at, updated_at)
VALUES
    (91001, 1, 'ScentStation Signature No. 01',
     'Hương gỗ hiện đại dành riêng cho hệ thống ScentStation.',
     'Cam bergamot, hoa diên vĩ, gỗ tuyết tùng, xạ hương',
     'https://images.unsplash.com/photo-1541643600914-78b084683601?auto=format&fit=crop&w=900&q=80',
     45000.00, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91006, 1, 'ScentStation Citrus Veil',
     'Làn hương cam chanh trong trẻo, tươi mới cho nhịp sống thành thị.',
     'Cam bergamot, quýt xanh, hoa cam, xạ hương trắng',
     'https://images.unsplash.com/photo-1590736704728-f4730bb30770?auto=format&fit=crop&w=900&q=80',
     39000.00, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91007, 1, 'ScentStation Rose Atelier',
     'Hương hoa thanh lịch với sắc hồng mềm mại và chiều sâu hiện đại.',
     'Hoa hồng, mẫu đơn, tiêu hồng, gỗ cashmere',
     'https://images.unsplash.com/photo-1587017539504-67cfbddac569?auto=format&fit=crop&w=900&q=80',
     42000.00, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91008, 1, 'ScentStation Amber Nuit',
     'Dấu ấn hổ phách ấm áp, quyến rũ dành cho những buổi tối đặc biệt.',
     'Hổ phách, vani Madagascar, đậu tonka, gỗ đàn hương',
     'https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?auto=format&fit=crop&w=900&q=80',
     48000.00, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91002, 9102, 'Bleu de Chanel Eau de Parfum',
     'Mùi hương nam tính, phóng khoáng và tinh tế.',
     'Bưởi, gừng, gỗ đàn hương, tuyết tùng',
     'https://images.unsplash.com/photo-1594035910387-fea47794261f?auto=format&fit=crop&w=900&q=80',
     65000.00, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91003, 9102, 'Coco Mademoiselle Eau de Parfum',
     'Hương phương Đông tươi sáng, thanh lịch và cuốn hút.',
     'Cam, hoa hồng, hoắc hương, cỏ hương bài',
     'https://images.unsplash.com/photo-1588405748880-12d1d2a59f75?auto=format&fit=crop&w=900&q=80',
     70000.00, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91004, 9103, 'Dior Sauvage Eau de Parfum',
     'Hương thơm mạnh mẽ lấy cảm hứng từ không gian thiên nhiên rộng lớn.',
     'Cam bergamot, tiêu Tứ Xuyên, oải hương, ambroxan',
     'https://images.unsplash.com/photo-1523293182086-7651a899d37f?auto=format&fit=crop&w=900&q=80',
     68000.00, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91005, 9103, 'J''adore Eau de Parfum',
     'Bó hoa nữ tính biểu tượng với sắc hoa phong phú và mềm mại.',
     'Hoàng lan, hoa hồng Damascus, nhài Sambac',
     'https://images.unsplash.com/photo-1563170351-be82bc888aa4?auto=format&fit=crop&w=900&q=80',
     72000.00, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    tenant_id = VALUES(tenant_id),
    name = VALUES(name),
    description = VALUES(description),
    fragrance_notes = VALUES(fragrance_notes),
    image_url = VALUES(image_url),
    default_price = VALUES(default_price),
    available = VALUES(available),
    updated_at = CURRENT_TIMESTAMP(6);

INSERT INTO campaigns
    (id, tenant_id, product_id, name, discount_type, discount_value, start_at, end_at, active, created_at, updated_at)
VALUES
    (91101, 1, 91001, 'Chào hè cùng Signature', 'PERCENTAGE', 12.00,
     CURRENT_TIMESTAMP(6) - INTERVAL 7 DAY, CURRENT_TIMESTAMP(6) + INTERVAL 30 DAY, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91102, 9102, 91002, 'Blue Weekend', 'PERCENTAGE', 10.00,
     CURRENT_TIMESTAMP(6) - INTERVAL 2 DAY, CURRENT_TIMESTAMP(6) + INTERVAL 21 DAY, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91103, 9102, 91003, 'Nét thanh lịch Paris', 'FIXED_AMOUNT', 8000.00,
     CURRENT_TIMESTAMP(6) - INTERVAL 1 DAY, CURRENT_TIMESTAMP(6) + INTERVAL 14 DAY, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91104, 9103, 91004, 'Sauvage Discovery', 'PERCENTAGE', 15.00,
     CURRENT_TIMESTAMP(6) - INTERVAL 5 DAY, CURRENT_TIMESTAMP(6) + INTERVAL 25 DAY, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91105, 9103, 91005, 'J''adore trở lại', 'FIXED_AMOUNT', 10000.00,
     CURRENT_TIMESTAMP(6) + INTERVAL 7 DAY, CURRENT_TIMESTAMP(6) + INTERVAL 37 DAY, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    tenant_id = VALUES(tenant_id),
    product_id = VALUES(product_id),
    name = VALUES(name),
    discount_type = VALUES(discount_type),
    discount_value = VALUES(discount_value),
    start_at = VALUES(start_at),
    end_at = VALUES(end_at),
    active = VALUES(active),
    updated_at = CURRENT_TIMESTAMP(6);
