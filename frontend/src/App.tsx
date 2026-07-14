import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import ProtectedRoute from "./components/ProtectedRoute";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Products from "./pages/Products";
import Unauthorized from "./components/Unauthorized.tsx";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<Login />} />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute requiredRole="report:view">
              <Dashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="/products"
          element={
            <ProtectedRoute requiredRole="product:view">
              <Products />
            </ProtectedRoute>
          }
        />
        <Route path="/unauthorized" element={<Unauthorized />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;