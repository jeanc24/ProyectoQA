import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import ProtectedRoute from "./components/ProtectedRoute";
import Landing from "./pages/Landing";
import TechGuide from "./pages/TechGuide";
import Products from "./pages/Products";
import Dashboard from "./pages/Dashboard";
import Stock from "./pages/Stock";
import Unauthorized from "./components/Unauthorized.tsx";
import { PERMISSIONS } from "./auth/permissions";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/login" element={<Landing />} />
        <Route path="/guide" element={<TechGuide />} />
        <Route path="/guide/:techId" element={<TechGuide />} />
        <Route
          path="/management"
          element={
            <ProtectedRoute>
              <Navigate to="/products" replace />
            </ProtectedRoute>
          }
        />
        <Route
          path="/products"
          element={
            <ProtectedRoute requiredRole={PERMISSIONS.productView}>
              <Products />
            </ProtectedRoute>
          }
        />
        <Route
          path="/stock"
          element={
            <ProtectedRoute requiredRole={PERMISSIONS.stockView}>
              <Stock />
            </ProtectedRoute>
          }
        />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute requiredRole={PERMISSIONS.reportView}>
              <Dashboard />
            </ProtectedRoute>
          }
        />
        <Route path="/unauthorized" element={<Unauthorized />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
