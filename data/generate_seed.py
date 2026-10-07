#!/usr/bin/env python3
"""Build the deterministic laptop seed used by the Spring app."""

from __future__ import annotations

import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any

import pandas as pd

ROOT = Path(__file__).resolve().parent.parent
RAW_CSV = ROOT / "data" / "raw" / "cleaned_laptops_dataset.csv"
SEED_PATH = ROOT / "backend" / "src" / "main" / "resources" / "dataseed.json"
ALLOWED_USE_CASES = [
    "student",
    "browsing",
    "light work",
    "productivity",
    "business",
    "programming",
    "multitasking",
    "video editing",
    "gaming",
    "travel",
]


def strip_invisible(value: Any) -> str:
    if value is None or pd.isna(value):
        return ""
    return str(value).replace("\u200e", "").strip()


def canonical_brand(raw: Any) -> str:
    brand = strip_invisible(raw).lower()
    mapping = {
        "acer": "Acer",
        "apple": "Apple",
        "asus": "ASUS",
        "dell": "Dell",
        "hp": "HP",
        "lenovo": "Lenovo",
        "microsoft": "Microsoft",
        "msi": "MSI",
        "realme": "Realme",
        "samsung": "Samsung",
        "tecno": "Tecno",
        "xiaomi": "Xiaomi",
    }
    return mapping.get(brand, brand.title())


def assign_cpu_tier(cpu_name: Any, brand: str) -> str:
    value = strip_invisible(cpu_name).lower()
    if not value and brand.lower() == "apple":
        return "high"
    if any(token in value for token in ["m1", "m2", "m3", "m4", "i9", "i7", "ultra 9", "ultra 7", "ryzen 9", "ryzen 7"]):
        return "high"
    if any(token in value for token in ["i5", "ultra 5", "ryzen 5", "core ultra 5"]):
        return "medium"
    if any(token in value for token in ["i3", "celeron", "pentium", "ryzen 3", "core ultra 3"]):
        return "low"
    if any(token in value for token in ["intel", "amd", "core"]):
        return "medium"
    return "unknown"


def resolution_label(width: float, height: float) -> str:
    pixels = float(width or 0) * float(height or 0)
    if pixels >= 3840 * 2160:
        return "4K"
    if pixels >= 2560 * 1600:
        return "2K"
    if pixels >= 1920 * 1080:
        return "Full HD"
    return "HD"


def infer_dedicated_gpu(graphics_brand: Any, gaming_flag: int) -> bool:
    brand = strip_invisible(graphics_brand).lower()
    if brand == "nvidia":
        return True
    if brand == "amd":
        return bool(gaming_flag)
    return False


def derive_use_cases(row: dict[str, Any]) -> list[str]:
    ram = float(row.get("ram_gb", 0) or 0)
    price = float(row.get("price", 0) or 0)
    weight = float(row.get("weight_kg", 0) or 0)
    cpu_tier = str(row.get("cpu_tier", "unknown")).lower()
    gaming_flag = int(row.get("gaming_flag", 0) or 0)
    business_flag = int(row.get("business_flag", 0) or 0)
    everyday_flag = int(row.get("everyday_use_flag", 0) or 0)
    name_l = str(row.get("name", "")).lower()
    os_name = str(row.get("os", "")).strip().lower()
    dedicated_gpu = bool(row.get("dedicated_gpu", False))
    use_cases: list[str] = []

    if gaming_flag or (dedicated_gpu and ram >= 16 and cpu_tier in {"medium", "high"}):
        use_cases.append("gaming")
    if dedicated_gpu and ram >= 16 and float(row.get("screen_size", 0) or 0) >= 14:
        use_cases.append("video editing")
    if (ram >= 16 and cpu_tier in {"medium", "high"}) or (ram >= 8 and cpu_tier == "high") or dedicated_gpu:
        use_cases.append("programming")
    if ram >= 16:
        use_cases.append("multitasking")
    if business_flag or any(token in name_l for token in ["thinkpad", "thinkbook", "latitude", "elitebook", "probook", "vostro", "precision", "zbook", "expertbook", "travelmate"]) or (
        not gaming_flag and not dedicated_gpu and ram >= 8 and 1.3 <= weight <= 1.8 and 35000 <= price <= 120000 and os_name in {"windows", "windows 11", "windows 10"}
    ):
        use_cases.append("business")
    if everyday_flag or (price <= 55000 and ram >= 8 and not gaming_flag):
        use_cases.append("student")
    if everyday_flag or cpu_tier == "low" or price <= 50000:
        use_cases.append("browsing")
    if cpu_tier in {"medium", "high"} and ram >= 8:
        use_cases.append("productivity")
    if weight <= 1.4:
        use_cases.append("travel")
    if cpu_tier == "low" and ram <= 8:
        use_cases.append("light work")

    ordered = []
    for case in ALLOWED_USE_CASES:
        if case in use_cases and case not in ordered:
            ordered.append(case)
    return ordered[:5]


def stable_laptop_id(brand: str, name: str, price: float, ram: float, storage: float, cpu: str) -> str:
    key = f"{brand}|{name}|{price}|{ram}|{storage}|{cpu}".encode("utf-8")
    return "l-" + hashlib.sha1(key).hexdigest()[:12]


def price_bucket(price: float) -> str:
    if price < 45000:
        return "entry-level"
    if price <= 90000:
        return "mid-range"
    if price <= 150000:
        return "high-end"
    return "premium"


def build_seed() -> dict[str, Any]:
    df = pd.read_csv(RAW_CSV)
    print(f"Start: {len(df)}")
    df = df.drop_duplicates().copy()
    print(f"After duplicate removal: {len(df)}")
    df = df.dropna(subset=["price", "rom_capacity_gb", "internal_memory_gb", "screen_size"]).copy()
    print(f"After missing-value filtering: {len(df)}")
    df["brand"] = df["brand"].map(canonical_brand)
    df["name"] = df["name"].map(strip_invisible)
    df["os"] = df["os"].map(strip_invisible)
    df = df[df["os"].str.lower().isin(["windows 11", "windows 10", "mac", "mac os", "mac os x", "windows"])].copy()
    print(f"After OS filter: {len(df)}")
    df["brand_count"] = df["brand"].map(df["brand"].value_counts())
    df = df[df["brand_count"] >= 5].copy()
    print(f"After brand threshold: {len(df)}")
    df["price"] = pd.to_numeric(df["price"], errors="coerce")
    df = df[(df["price"] >= 20000) & (df["price"] <= 200000)].copy()
    print(f"After price range: {len(df)}")
    df = df[df["weight_kg"].notna()].copy()
    print(f"After weight filter: {len(df)}")

    df["ram_gb"] = pd.to_numeric(df["rom_capacity_gb"], errors="coerce")
    df["storage_gb"] = pd.to_numeric(df["internal_memory_gb"], errors="coerce")
    df["weight_kg"] = pd.to_numeric(df["weight_kg"], errors="coerce")
    df["screen_size"] = pd.to_numeric(df["screen_size"], errors="coerce")
    df["battery_Wh"] = pd.to_numeric(df["battery_Wh"], errors="coerce")
    df["touch_screen"] = pd.to_numeric(df["touch_screen"], errors="coerce")
    df["resolution_WIDTH"] = pd.to_numeric(df["resolution_WIDTH"], errors="coerce")
    df["resolution_HEIGHT"] = pd.to_numeric(df["resolution_HEIGHT"], errors="coerce")
    df["display_width"] = df[["resolution_WIDTH", "resolution_HEIGHT"]].max(axis=1)
    df["display_height"] = df[["resolution_WIDTH", "resolution_HEIGHT"]].min(axis=1)
    df["processor_core"] = df["processor_core"].map(strip_invisible)
    df["processor_brand"] = df["processor_brand"].map(strip_invisible)
    df["processor_name"] = df["processor_core"].fillna(df["processor_brand"])
    df["cpu_tier"] = df.apply(lambda row: assign_cpu_tier(row["processor_name"], row["brand"]), axis=1)
    df["gaming_flag"] = pd.to_numeric(df["utility: Gaming"], errors="coerce").fillna(0).astype(int)
    df["business_flag"] = pd.to_numeric(df["utility: Business"], errors="coerce").fillna(0).astype(int)
    df["everyday_use_flag"] = pd.to_numeric(df["utility: Everyday Use"], errors="coerce").fillna(0).astype(int)
    df["graphics_brand"] = df["graphics_brand"].map(strip_invisible)
    df["dedicated_gpu"] = df.apply(lambda row: infer_dedicated_gpu(row["graphics_brand"], int(row["gaming_flag"])), axis=1)

    df["use_cases"] = df.apply(derive_use_cases, axis=1)
    df["main_use_case"] = df["use_cases"].apply(lambda values: values[0] if values else "student")
    df["unique_name"] = df["brand"].astype(str) + " " + df["name"].astype(str)
    seen = {}
    for idx, row in df.iterrows():
        key = str(row["unique_name"]).lower()
        if seen.get(key, 0) == 0:
            seen[key] = 1
        else:
            cpu = str(row.get("processor_name", "CPU")).strip() or "CPU"
            ram = int(float(row.get("ram_gb", 0) or 0))
            storage = int(float(row.get("storage_gb", 0) or 0))
            df.at[idx, "unique_name"] = f"{row['unique_name']} {cpu} {ram}GB/{storage}GB"

    df["selection_score"] = 0.0
    for idx, row in df.iterrows():
        score = 0.0
        score += 5 if "gaming" in row["use_cases"] else 0
        score += 4 if "business" in row["use_cases"] else 0
        score += 4 if "programming" in row["use_cases"] else 0
        score += 3 if "travel" in row["use_cases"] else 0
        score += float(row.get("user_rating", 0) or 0) * 5
        score += float(row.get("user_votes", 0) or 0) / 50.0
        score += float(row.get("ram_gb", 0) or 0) / 5.0
        score += float(row.get("storage_gb", 0) or 0) / 100.0
        score -= abs(float(row.get("price", 0) or 0) - 70000) / 20000
        df.at[idx, "selection_score"] = score

    selected_idx = set()
    required = [
        ("gaming", lambda d: d["use_cases"].apply(lambda items: "gaming" in items), 25),
        ("business", lambda d: d["use_cases"].apply(lambda items: "business" in items), 25),
        ("under_45000", lambda d: d["price"] < 45000, 30),
        ("travel", lambda d: d["use_cases"].apply(lambda items: "travel" in items), 15),
        ("programming", lambda d: d["use_cases"].apply(lambda items: "programming" in items), 40),
        ("video editing", lambda d: d["use_cases"].apply(lambda items: "video editing" in items), 20),
        ("apple", lambda d: d["brand"] == "Apple", 3),
    ]
    for _, predicate, target in required:
        subset = df[predicate(df)].sort_values(["selection_score", "user_rating", "user_votes"], ascending=[False, False, False]).head(target)
        selected_idx.update(subset.index.tolist())

    remaining = df[~df.index.isin(selected_idx)].sort_values(["selection_score", "user_rating", "user_votes"], ascending=[False, False, False])
    for idx in remaining.index:
        if len(selected_idx) >= 200:
            break
        selected_idx.add(idx)

    if len(selected_idx) < 150:
        raise ValueError(f"Selection produced too few laptops: {len(selected_idx)}")
    final = df[df.index.isin(selected_idx)].copy().reset_index(drop=True)
    final = final.sort_values(["price", "brand", "name"], kind="mergesort").reset_index(drop=True)

    final["rating"] = pd.to_numeric(final["user_rating"], errors="coerce").fillna(4.0)
    final["votes"] = pd.to_numeric(final["user_votes"], errors="coerce").fillna(100)
    final["bestseller"] = (final["rating"] >= 4.4) & (final["votes"] >= 100)
    final["inventory"] = 0
    final["clear_overstock"] = False
    for idx, row in final.iterrows():
        bucket = price_bucket(float(row["price"]))
        if bucket == "entry-level":
            margin = 0.12 + (idx % 5) * 0.01
        elif bucket == "mid-range":
            margin = 0.14 + (idx % 4) * 0.015
        elif bucket == "high-end":
            margin = 0.16 + (idx % 3) * 0.02
        else:
            margin = 0.18 + (idx % 3) * 0.02
        if idx % 11 == 0:
            margin = 0.09 + (idx % 2) * 0.005
        inventory = 8 + ((idx + 7) * 7) % 53
        if idx % 25 == 0:
            inventory = 0
        elif idx % 8 == 0:
            inventory = 1 + ((idx * 3) % 5)
        final.at[idx, "margin_pct"] = margin
        final.at[idx, "inventory"] = int(inventory)
        final.at[idx, "clear_overstock"] = bool(inventory >= final["inventory"].quantile(0.8))
        final.at[idx, "cost"] = round(float(row["price"]) * (1 - margin))

    final["productId"] = ""
    for idx, row in final.iterrows():
        cpu = str(row.get("processor_name", "CPU")).strip() or "CPU"
        final.at[idx, "productId"] = stable_laptop_id(row["brand"], row["unique_name"], float(row["price"]), float(row["ram_gb"]), float(row["storage_gb"]), cpu)
    if len(set(final["productId"])) != len(final):
        raise ValueError("Laptop ids collided")

    laptop_products = []
    for idx, row in final.iterrows():
        use_cases = [case for case in ALLOWED_USE_CASES if case in row["use_cases"]]
        if not use_cases:
            use_cases = ["student"]
        tags = [price_bucket(float(row["price"]))]
        if "video editing" in use_cases or "programming" in use_cases:
            tags.append("creator")
        if "programming" in use_cases or "multitasking" in use_cases:
            tags.append("developer")
        if "gaming" in use_cases:
            tags.append("gaming")
        if bool(row["bestseller"]):
            tags.append("bestseller")
        if bool(row["clear_overstock"]):
            tags.append("clear_overstock")

        product = {
            "productId": row["productId"],
            "name": row["unique_name"],
            "category": "laptop",
            "price": int(float(row["price"])),
            "cost": int(float(row["cost"])),
            "inventory": int(row["inventory"]),
            "ramGb": int(float(row["ram_gb"])),
            "storageGb": int(float(row["storage_gb"])),
            "weightKg": float(row["weight_kg"]),
            "screenSizeInch": float(row["screen_size"]),
            "cpuTier": str(row["cpu_tier"]).lower(),
            "dedicatedGpu": bool(row["dedicated_gpu"]),
            "batteryWh": float(row["battery_Wh"]) if pd.notna(row["battery_Wh"]) else None,
            "touchScreen": bool(int(row["touch_screen"])) if pd.notna(row["touch_screen"]) else None,
            "userRating": float(row["rating"]),
            "ratingCount": int(row["votes"]),
            "features": [
                f"{str(row.get('processor_name', 'CPU')).strip() or 'CPU'} processor",
                f"{int(float(row.get('ram_gb', 0) or 0))}GB RAM",
                f"{int(float(row.get('storage_gb', 0) or 0))}GB storage",
                f"{float(row.get('screen_size', 0) or 0):.1f}-inch display",
                f"{resolution_label(float(row.get('display_width', 0) or 0), float(row.get('display_height', 0) or 0))} display",
                "Touchscreen" if bool(row["touch_screen"]) else None,
                f"{str(row.get('graphics_brand', '')).strip() or 'Integrated'} graphics",
                f"{float(row.get('weight_kg', 0) or 0):.1f}kg weight",
                f"{float(row.get('battery_Wh', 0) or 0):.1f}Wh battery" if pd.notna(row.get('battery_Wh')) and row.get('battery_Wh') not in [None, ''] else None,
            ],
            "useCases": use_cases,
            "tags": tags,
            "crossSell": [],
            "upsell": [],
        }
        product["features"] = [item for item in product["features"] if item is not None]
        if float(row["weight_kg"]) <= 1.4:
            product["crossSell"] = ["p10", "p21"]
        elif "gaming" in use_cases:
            product["crossSell"] = ["p13", "p14", "p19"]
        elif "video editing" in use_cases or "programming" in use_cases:
            product["crossSell"] = ["p11", "p14", "p19"]
        elif "business" in use_cases or "productivity" in use_cases:
            product["crossSell"] = ["p9", "p12", "p11"]
        else:
            product["crossSell"] = ["p9", "p10", "p21"]
        laptop_products.append(product)

    by_use = {}
    for product in laptop_products:
        main_use = product["useCases"][0]
        by_use.setdefault(main_use, []).append(product)
    for product in laptop_products:
        main_use = product["useCases"][0]
        best = None
        best_gap = None
        for candidate in by_use.get(main_use, []):
            if candidate["productId"] == product["productId"]:
                continue
            ratio = float(candidate["price"]) / max(float(product["price"]), 1)
            if not (1.10 <= ratio <= 1.45):
                continue
            ram_current = int(float(product["features"][1].split("GB")[0]))
            ram_other = int(float(candidate["features"][1].split("GB")[0]))
            if ram_other <= ram_current:
                continue
            gap = abs(ratio - 1.25)
            if best_gap is None or gap < best_gap:
                best = candidate["productId"]
                best_gap = gap
        if best:
            product["upsell"] = [best]

    if SEED_PATH.exists():
        with SEED_PATH.open("r", encoding="utf-8") as handle:
            existing = json.load(handle)
    else:
        existing = {"merchant": {}, "products": []}
    merchant = existing.get("merchant") or {
        "merchantId": "MERCH-001",
        "minMarginPercent": 12.0,
        "maxDiscountPercent": 10.0,
        "discountSteps": [0.0, 5.0, 10.0],
        "primaryObjective": "clear_overstock",
    }
    accessories = [
        item for item in existing.get("products", []) if str(item.get("category", "")).lower() == "accessory"
    ]
    if not accessories:
        accessories = [
            {"productId": "p9", "name": "Ergo Wireless Mouse", "category": "accessory", "price": 2000, "cost": 1000, "inventory": 50, "features": ["Wireless", "Ergonomic", "Optical Sensor"], "useCases": ["productivity", "travel"], "tags": ["accessory"], "crossSell": [], "upsell": []},
            {"productId": "p10", "name": "Premium Laptop Backpack", "category": "accessory", "price": 3500, "cost": 1500, "inventory": 30, "features": ["Waterproof", "Fits up to 15.6-inch", "Padded Compartment"], "useCases": ["travel", "work"], "tags": ["accessory", "clear_overstock"], "crossSell": [], "upsell": []},
            {"productId": "p11", "name": "Type-C 7-in-1 Hub", "category": "accessory", "price": 2500, "cost": 1200, "inventory": 100, "features": ["USB-C", "HDMI 4K", "USB 3.0 x3", "SD Card Reader"], "useCases": ["productivity"], "tags": ["accessory"], "crossSell": [], "upsell": []},
            {"productId": "p12", "name": "Bluetooth Mechanical Keyboard", "category": "accessory", "price": 4500, "cost": 2800, "inventory": 25, "features": ["Bluetooth 5.0", "Mechanical Switches", "Backlit"], "useCases": ["productivity", "gaming"], "tags": ["accessory", "bestseller"], "crossSell": [], "upsell": []},
            {"productId": "p13", "name": "Laptop Cooling Pad", "category": "accessory", "price": 1800, "cost": 900, "inventory": 45, "features": ["Dual Fan", "Adjustable Height", "USB Powered"], "useCases": ["gaming"], "tags": ["accessory"], "crossSell": [], "upsell": []},
            {"productId": "p14", "name": "27-inch FHD Monitor", "category": "accessory", "price": 12000, "cost": 9000, "inventory": 12, "features": ["27-inch", "Full HD", "75Hz Refresh Rate", "HDMI + VGA"], "useCases": ["productivity", "business", "gaming"], "tags": ["accessory"], "crossSell": [], "upsell": []},
            {"productId": "p19", "name": "34-inch Curved Monitor", "category": "accessory", "price": 22000, "cost": 17000, "inventory": 9, "features": ["34-inch", "Curved Ultrawide", "144Hz Refresh Rate", "USB-C"], "useCases": ["video editing", "gaming", "productivity"], "tags": ["accessory", "premium"], "crossSell": [], "upsell": []},
            {"productId": "p20", "name": "100W USB-C Charging Cable", "category": "accessory", "price": 1200, "cost": 600, "inventory": 80, "features": ["100W Fast Charging", "2m Braided Cable", "USB-C to USB-C"], "useCases": ["travel", "productivity"], "tags": ["accessory"], "crossSell": [], "upsell": []},
            {"productId": "p21", "name": "14-inch Laptop Sleeve", "category": "accessory", "price": 1500, "cost": 700, "inventory": 60, "features": ["Neoprene", "Shock-resistant", "Slim Fit"], "useCases": ["travel", "student"], "tags": ["accessory", "clear_overstock"], "crossSell": [], "upsell": []},
            {"productId": "p22", "name": "ANC Wireless Headphones", "category": "accessory", "price": 5500, "cost": 3200, "inventory": 20, "features": ["Active Noise Cancellation", "30H Battery", "Bluetooth 5.2"], "useCases": ["travel", "productivity"], "tags": ["accessory", "bestseller"], "crossSell": [], "upsell": []},
        ]

    payload = {"merchant": merchant, "products": accessories + laptop_products}
    product_ids = [item["productId"] for item in payload["products"]]
    if len(product_ids) != len(set(product_ids)):
        raise ValueError("Duplicate product IDs found")
    for product in laptop_products:
        for ref in product["crossSell"]:
            if ref not in product_ids:
                raise ValueError(f"Cross-sell reference {ref} missing for {product['productId']}")
        if product["productId"] in product["upsell"]:
            raise ValueError(f"Laptop {product['productId']} cannot upsell itself")
        for ref in product["upsell"]:
            if ref not in product_ids:
                raise ValueError(f"Upsell reference {ref} missing for {product['productId']}")
    if not 150 <= len(laptop_products) <= 250:
        raise ValueError(f"Expected 150-250 laptops, got {len(laptop_products)}")

    with SEED_PATH.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, ensure_ascii=False)
        handle.write("\n")

    print(f"Laptop count: {len(laptop_products)}")
    print(f"Use-case counts: {dict(Counter(use_case for product in laptop_products for use_case in product['useCases']))}")
    print(f"Price tier counts: {dict(Counter(product['tags'][0] for product in laptop_products))}")
    return payload


if __name__ == "__main__":
    build_seed()
