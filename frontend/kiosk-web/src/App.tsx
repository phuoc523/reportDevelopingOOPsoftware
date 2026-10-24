
import { useMemo, useState } from "react";
import "./App.css";

type Product = {
  id: number;
  brand: string;
  name: string;
  category: string;
  notes: string;
  price: number;
  samplePrice: number;
  volume: number;
  remainingVolume: number;
  stock: number;
  image: string;
  color: string;
};

const initialProducts: Product[] = [
  {
    id: 1,
    brand: "MAISON LUMIÈRE",
    name: "Santal No. 04",
    category: "Woody",
    notes: "Gỗ đàn hương · Hổ phách · Xạ hương",
    price: 3200000,
    samplePrice: 25000,
    volume: 100,
    remainingVolume: 68,
    stock: 5,
    image: "https://images.unsplash.com/photo-1594035910387-fea47794261f?auto=format&fit=crop&w=900&q=85",
    color: "#e7dfd2",
  },
  {
    id: 2,
    brand: "ATELIER BOTANIQUE",
    name: "Rose Éternelle",
    category: "Floral",
    notes: "Hoa hồng · Hoa nhài · Xạ hương trắng",
    price: 2850000,
    samplePrice: 20000,
    volume: 75,
    remainingVolume: 12,
    stock: 0,
    image: "https://images.unsplash.com/photo-1541643600914-78b084683601?auto=format&fit=crop&w=900&q=85",
    color: "#ead9d7",
  },
  {
    id: 3,
    brand: "NOIR STUDIO",
    name: "Midnight Oud",
    category: "Oriental",
    notes: "Trầm hương · Nghệ tây · Da thuộc",
    price: 4100000,
    samplePrice: 30000,
    volume: 100,
    remainingVolume: 0,
    stock: 0,
    image: "https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?auto=format&fit=crop&w=900&q=85",
    color: "#d9d0c6",
  },
  {
    id: 4,
    brand: "MAISON LUMIÈRE",
    name: "Citrus No. 08",
    category: "Fresh",
    notes: "Cam bergamot · Chanh · Gỗ tuyết tùng",
    price: 2450000,
    samplePrice: 15000,
    volume: 75,
    remainingVolume: 43,
    stock: 4,
    image: "https://images.unsplash.com/photo-1594035910387-fea47794261f?auto=format&fit=crop&w=900&q=85",
    color: "#e5e2cf",
  },
  {
    id: 5,
    brand: "ATELIER BOTANIQUE",
    name: "Vanille Douce",
    category: "Gourmand",
    notes: "Vani · Hạnh nhân · Đậu tonka",
    price: 2950000,
    samplePrice: 20000,
    volume: 100,
    remainingVolume: 31,
    stock: 2,
    image: "https://images.unsplash.com/photo-1595425970377-c9703cf48b6d?auto=format&fit=crop&w=900&q=85",
    color: "#e8d9c5",
  },
  {
    id: 6,
    brand: "NOIR STUDIO",
    name: "Blue Horizon",
    category: "Fresh",
    notes: "Biển xanh · Oải hương · Gỗ tuyết tùng",
    price: 2650000,
    samplePrice: 18000,
    volume: 75,
    remainingVolume: 25,
    stock: 5,
    image: "https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?auto=format&fit=crop&w=900&q=85",
    color: "#d6dfe0",
  },
];

const formatPrice = (price: number) =>
  new Intl.NumberFormat("vi-VN").format(price) + " ₫";

function App() {
  const [products] = useState(initialProducts);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("All");
  const [availability, setAvailability] = useState("All");
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [action, setAction] = useState<"try" | "buy" | null>(null);
  const [notice, setNotice] = useState("");

  const categories = ["All", "Woody", "Floral", "Oriental", "Fresh", "Gourmand"];

  const filteredProducts = useMemo(() => {
    return products.filter((product) => {
      const query = search.toLowerCase();
      const matchesSearch =
        product.name.toLowerCase().includes(query) ||
        product.brand.toLowerCase().includes(query) ||
        product.notes.toLowerCase().includes(query);

      const matchesCategory =
        category === "All" || product.category === category;

      const matchesAvailability =
        availability === "All" ||
        (availability === "Available" && product.stock > 0) ||
        (availability === "Unavailable" && product.stock === 0);

      return matchesSearch && matchesCategory && matchesAvailability;
    });
  }, [products, search, category, availability]);

  function openAction(product: Product, selectedAction: "try" | "buy") {
    setNotice("");
    setSelectedProduct(product);
    setAction(selectedAction);
  }

  function closeDialog() {
    setSelectedProduct(null);
    setAction(null);
    setNotice("");
  }

  function continueAction() {
  if (!selectedProduct || !action) return;

  if (selectedProduct.stock === 0) {
    setNotice(
      "Sản phẩm hiện đã hết hàng. Vui lòng chọn mùi hương khác.",
    );
    return;
  }

    setNotice(
      action === "try"
        ? "Đã xác nhận lựa chọn thử mùi trong bản demo. Bước tiếp theo sẽ kết nối API tạo đơn và thanh toán."
        : "Đã xác nhận lựa chọn mua hàng trong bản demo. Bước tiếp theo sẽ kết nối API tạo đơn và thanh toán.",
    );
  }

  return (
    <main className="store">
      <header className="topbar">
        <a className="wordmark" href="#" aria-label="ScentStation trang chủ">
          <span className="brand-symbol">S.</span>
          <span>SCENTSTATION</span>
        </a>

        <nav className="topnav">
          <a href="#collection">Bộ sưu tập</a>
          <a href="#how-it-works">Cách sử dụng</a>
        </nav>

        <div className="topbar-right">
          <span className="status-dot" />
          <span>Trải nghiệm hương thơm</span>
        </div>
      </header>

      <section className="hero">
        <div className="hero-copy">
          <p className="eyebrow">YOUR SCENT, YOUR STORY</p>
          <h1>
            Tìm thấy
            <br />
            <em>hương thơm</em> của bạn.
          </h1>
          <p className="hero-description">
            Khám phá những mùi hương được tuyển chọn. Thử trước khi quyết định,
            hoặc chọn mua hương thơm yêu thích của bạn.
          </p>
          <a className="primary-button" href="#collection">
            Khám phá bộ sưu tập <span>↗</span>
          </a>
          <div className="hero-caption">
            <span>01 — DISCOVER</span>
            <span>02 — EXPERIENCE</span>
            <span>03 — CHOOSE</span>
          </div>
        </div>

        <div className="hero-art">
          <div className="hero-circle" />
          <img
            className="hero-image"
            src="https://images.unsplash.com/photo-1594035910387-fea47794261f?auto=format&fit=crop&w=1000&q=90"
            alt="Chai nước hoa trong không gian tối giản"
          />
          <div className="hero-note">
            <span>THE ART OF</span>
            <strong>Fragrance</strong>
            <span>DISCOVER YOUR SIGNATURE</span>
          </div>
          <span className="hero-index">COLLECTION N° 01</span>
        </div>
      </section>

      <section className="collection" id="collection">
        <div className="section-heading">
          <div>
            <p className="eyebrow">THE COLLECTION</p>
            <h2>Hương thơm dành cho bạn.</h2>
            <p className="section-description">
              Chọn một mùi hương, trải nghiệm theo cách của riêng bạn.
            </p>
          </div>
          <span className="product-count">
            {filteredProducts.length.toString().padStart(2, "0")} SẢN PHẨM
          </span>
        </div>

        <div className="filters">
          <label className="search-box">
            <span aria-hidden="true">⌕</span>
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Tìm nước hoa, thương hiệu..."
              aria-label="Tìm sản phẩm"
            />
          </label>

          <div className="category-filters" aria-label="Lọc nhóm hương">
            {categories.map((item) => (
              <button
                className={category === item ? "filter active" : "filter"}
                key={item}
                onClick={() => setCategory(item)}
              >
                {item === "All" ? "Tất cả" : item}
              </button>
            ))}
          </div>

          <select
            className="availability-filter"
            value={availability}
            onChange={(event) => setAvailability(event.target.value)}
            aria-label="Lọc tình trạng tồn kho"
          >
            <option value="All">Mọi trạng thái</option>
            <option value="Available">Còn hàng</option>
            <option value="Unavailable">Hết hàng</option>
          </select>
        </div>

        <div className="product-grid">
          {filteredProducts.map((product, index) => {
            const outOfStock = product.stock === 0;
            const lowStock = product.stock > 0 && product.stock <= 2;

            return (
              <article className="product-card" key={product.id}>
                <div
                  className="product-visual"
                  style={{ backgroundColor: product.color }}
                >
                  <img src={product.image} alt={product.name} loading="lazy" />
                  <span className="product-index">
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <span
                    className={
                      outOfStock
                        ? "stock-badge unavailable"
                        : lowStock
                          ? "stock-badge low"
                          : "stock-badge"
                    }
                  >
                    <span className="stock-dot" />
                    {outOfStock
                      ? "Hết hàng"
                      : lowStock
                        ? "Sắp hết hàng"
                        : "Còn hàng"}
                  </span>
                  <button
                    className="quick-view"
                    onClick={() => openAction(product, "try")}
                    aria-label={`Khám phá ${product.name}`}
                    disabled={outOfStock}
                  >
                    ↗
                  </button>
                </div>

                <div className="product-info">
                  <p className="product-brand">{product.brand}</p>
                  <h3>{product.name}</h3>
                  <p className="product-notes">{product.notes}</p>

                  
                  <div className="product-meta">
                    <span className="volume-label">Dung tích chai</span>
                    <strong className="volume-value">{product.volume} ml</strong>
                  </div>
         
                  <div className="price-row">
                    <div className="sample-price">
                      <span className="price-label">Phí thử</span>
                      <strong>{formatPrice(product.samplePrice)}</strong>
                    </div>
                    <div>
                      <span className="price-label">Giá mua</span>
                      <strong>{formatPrice(product.price)}</strong>
                    </div>
                  </div>


                  <div className="product-actions">
                    <button
                      className="try-button"
                      disabled={outOfStock}
                      onClick={() => openAction(product, "try")}
                    >
                      Thử có phí
                    </button>
                    <button
                      className="buy-button"
                      disabled={outOfStock}
                      onClick={() => openAction(product, "buy")}
                    >
                      Mua ngay <span>↗</span>
                    </button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>

        {filteredProducts.length === 0 && (
          <div className="empty-state">
            <h3>Chưa tìm thấy mùi hương phù hợp</h3>
            <p>Thử từ khóa khác hoặc thay đổi bộ lọc sản phẩm.</p>
            <button
              className="try-button"
              onClick={() => {
                setSearch("");
                setCategory("All");
                setAvailability("All");
              }}
            >
              Xóa bộ lọc
            </button>
          </div>
        )}
      </section>

      <section className="how-it-works" id="how-it-works">
        <p className="eyebrow">A SIMPLE RITUAL</p>
        <h2>Ba bước. Một dấu ấn riêng.</h2>
        <div className="steps-grid">
          <div className="step">
            <span>01</span>
            <h3>Khám phá</h3>
            <p>Tìm hiểu nhóm hương, ghi chú và câu chuyện của từng sản phẩm.</p>
          </div>
          <div className="step">
            <span>02</span>
            <h3>Trải nghiệm</h3>
            <p>Chọn thử có phí hoặc mua trực tiếp theo nhu cầu của bạn.</p>
          </div>
          <div className="step">
            <span>03</span>
            <h3>Khẳng định</h3>
            <p>Tìm ra mùi hương phản ánh phong cách riêng của bạn.</p>
          </div>
        </div>
      </section>

      <footer className="footer">
        <a className="wordmark" href="#">
          <span className="brand-symbol">S.</span>
          <span>SCENTSTATION</span>
        </a>
        <p>Discover a scent that feels like you.</p>
        <span>© 2026 SCENTSTATION</span>
      </footer>

      {selectedProduct && action && (
        <div
          className="modal-backdrop"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) closeDialog();
          }}
        >
          <section
            className="action-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="modal-title"
          >
            <button
              className="modal-close"
              onClick={closeDialog}
              aria-label="Đóng"
            >
              ×
            </button>

            <p className="eyebrow">
              {action === "try" ? "DISCOVER YOUR SCENT" : "MAKE IT YOURS"}
            </p>
            <h2 id="modal-title">
              {action === "try" ? "Thử mùi hương" : "Mua nước hoa"}
            </h2>
            <p className="modal-product">
              {selectedProduct.brand} · {selectedProduct.name}
            </p>

            <div className="modal-summary">
              <span>
                {action === "try" ? "Phí thử dự kiến" : "Giá sản phẩm"}
              </span>
              <strong>
                {formatPrice(
                  action === "try"
                    ? selectedProduct.samplePrice
                    : selectedProduct.price,
                )}
              </strong>
              <span>
                {action === "try"
                  ? "Phí thử cho một lượt trải nghiệm."
                  : `Dung tích chai: ${selectedProduct.volume} ml.`}
              </span>
            </div>

            <p className="modal-stock">
              {selectedProduct.stock > 0
                ? `Tình trạng: Còn hàng · ${selectedProduct.remainingVolume} ml ước tính còn lại`
                : "Tình trạng: Hết hàng"}
            </p>

            {notice && <p className="modal-notice">{notice}</p>}

            <button
              className="buy-button modal-confirm"
              onClick={continueAction}
              disabled={selectedProduct.stock === 0}
            >
              Tiếp tục <span>↗</span>
            </button>
            <p className="modal-disclaimer">
              Bản demo: chưa tạo đơn, chưa thanh toán và chưa trừ tồn kho thật.
            </p>
          </section>
        </div>
      )}
    </main>
  );
}

export default App;
