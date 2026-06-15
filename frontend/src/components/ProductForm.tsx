import { useState, type FormEvent } from "react";
import type { ProductRequest } from "../types/product";

type Props = {
  initial?: ProductRequest;
  submitLabel: string;
  onSubmit: (data: ProductRequest) => Promise<void>;
  onCancel: () => void;
};

const emptyProduct = (): ProductRequest => ({
  name: "",
  sku: "",
  description: "",
  categoryId: null,
  price: 0,
  quantity: 0,
  minStock: 0,
  active: true,
});

export default function ProductForm({
  initial,
  submitLabel,
  onSubmit,
  onCancel,
}: Props) {
  const [form, setForm] = useState<ProductRequest>(initial ?? emptyProduct());
  const [saving, setSaving] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      await onSubmit(form);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="product-form" onSubmit={handleSubmit}>
      <div className="form-grid">
        <label>
          Nombre
          <input
            required
            maxLength={150}
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
        </label>

        <label>
          SKU
          <input
            required
            maxLength={50}
            value={form.sku}
            onChange={(e) => setForm({ ...form, sku: e.target.value })}
          />
        </label>

        <label>
          Precio
          <input
            required
            type="number"
            min={0}
            step="0.01"
            value={form.price}
            onChange={(e) =>
              setForm({ ...form, price: Number(e.target.value) })
            }
          />
        </label>

        <label>
          Stock (quantity)
          <input
            required
            type="number"
            min={0}
            value={form.quantity}
            onChange={(e) =>
              setForm({ ...form, quantity: Number(e.target.value) })
            }
          />
        </label>

        <label>
          Stock mínimo
          <input
            required
            type="number"
            min={0}
            value={form.minStock}
            onChange={(e) =>
              setForm({ ...form, minStock: Number(e.target.value) })
            }
          />
        </label>

        <label>
          Categoría (ID)
          <input
            type="number"
            min={1}
            value={form.categoryId ?? ""}
            onChange={(e) =>
              setForm({
                ...form,
                categoryId: e.target.value ? Number(e.target.value) : null,
              })
            }
          />
        </label>

        <label className="checkbox">
          <input
            type="checkbox"
            checked={form.active}
            onChange={(e) => setForm({ ...form, active: e.target.checked })}
          />
          Activo
        </label>
      </div>

      <label>
        Descripción
        <textarea
          value={form.description ?? ""}
          onChange={(e) => setForm({ ...form, description: e.target.value })}
        />
      </label>

      <div className="actions">
        <button type="submit" disabled={saving}>
          {saving ? "Guardando..." : submitLabel}
        </button>
        <button type="button" onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </form>
  );
}