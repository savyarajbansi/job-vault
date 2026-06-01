import { NavLink, Outlet } from "react-router-dom";

function navLinkClass({ isActive }: { isActive: boolean }): string {
  return isActive ? "active" : "";
}

export default function AppLayout() {
  return (
    <div>
      <nav>
        <NavLink to="/" end className={navLinkClass}>
          Home
        </NavLink>
        {" | "}
        <NavLink to="/auth" className={navLinkClass}>
          Auth
        </NavLink>
        {" | "}
        <NavLink to="/seeker" className={navLinkClass}>
          Seeker
        </NavLink>
        {" | "}
        <NavLink to="/admin/metrics" className={navLinkClass}>
          Admin
        </NavLink>
      </nav>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
