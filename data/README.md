# Data pipeline for the laptop catalog

## Source

This project uses the cleaned Kaggle laptop dataset stored at `data/raw/cleaned_laptops_dataset.csv`.

- Source: public Kaggle laptop dataset used for the Pivot catalog build
- Snapshot date: 2026-09-04
- License: owner must confirm and fill in the dataset license before public redistribution
- Notes: prices reflect the historical retail snapshot in rupees; CPU generations and model names are time-bound and should be treated as historical catalog facts rather than current market pricing

## What is real vs simulated

This repository intentionally mixes real dataset fields and a few simulated catalog attributes so that the demo remains deterministic and usable without a live retailer backend:

- Real dataset fields: brand, model name, price, weight, screen size, resolution, RAM, storage, user rating, user votes, operating system, graphics brand, battery, processor family, touch flag
- Simulated or rule-derived fields: cost, inventory, use-case labels, tags like `clear_overstock`, cross-sell and upsell links, and the dedicated-GPU inference used for some product classification
- Why this is acceptable: the app is a product recommendation engine, not a live inventory system, and the deterministic pricing and merchandising rules are what make the decisions auditable

## Rebuild steps

1. Install Python dependencies from `data/requirements.txt`.
2. Run `python data/generate_seed.py` from the repository root.
3. The script rewrites `backend/src/main/resources/dataseed.json` with a deterministic laptop seed and preserves the existing merchant block plus the 10 accessories.
4. Restart the Java backend if the catalog should be reloaded.

## Determinism and validation

The script is deterministic because it uses a fixed random seed and stable hashing for the generated laptop ids. It validates the final catalog before writing the file and fails with a clear message if:

- laptop ids are duplicated
- cross-sell or upsell references are missing
- use cases are outside the allowed vocabulary
- the generated catalog falls outside the 150-250 laptop range
- required coverage thresholds are not met

## Supported use-case vocabulary

- student
- browsing
- light work
- productivity
- business
- programming
- multitasking
- video editing
- gaming
- travel

## Notes

This version intentionally keeps the catalog scoped to laptops and the 10 accessories already used by the app; the merchant is kept as-is to match the current business rules.
