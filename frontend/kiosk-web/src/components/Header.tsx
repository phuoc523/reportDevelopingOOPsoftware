
import '../App.css'

function Header() {
  return (
    <header className="topbar">
      <a className="brand" href="#" aria-label="ScentStation trang chủ">
        <span className="brand-mark">S</span>
        <span>
          <strong>SCENTSTATION</strong>
          <small>YOUR SCENT, YOUR MOMENT</small>
        </span>
      </a>

      <div className="machine-status">
        <span className="status-dot" />
        <span>TRẠM HƯƠNG THƠM</span>
      </div>
    </header>
  )
}

export default Header
