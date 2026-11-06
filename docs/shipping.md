# Shipping and parcel machines

**Status:** **Omniva is built** (Estonia, Latvia, Lithuania). SmartPost, DPD and Unisend are planned.
Checkout asks for name, email, mobile phone, country, delivery method and parcel machine. There is no
street address any more. Delivery costs €4.99 and is free from €50 (config: `app.shipping.*`).

## Goal

Delivery to **parcel machines (pakiautomaat)**, with carriers that can be swapped: **Omniva,
SmartPost (Itella), DPD, Unisend**, and others later. Adding a carrier must not require changes to
checkout or frontend code.

## Design

### Backend interface (`com.webshop.shipping`)

```java
public interface ShippingCarrier {
    String code();                       // "omniva", "smartpost", "dpd", "unisend", ...
    String displayName();
    Set<String> countries();             // e.g. EE, LV, LT

    /** Parcel machines for a country. Cache this: carriers publish lists that change rarely. */
    List<PickupPoint> pickupPoints(String countryCode);

    /** Delivery price before the shop-wide free-from-€50 rule (applied by ShippingService). */
    Money basePrice();

    // Later: register the shipment with the carrier and get a tracking number / label.
}
```

- `PickupPoint`: `{ id, carrierCode, name, address, city, countryCode, latitude, longitude }`
- `ShippingService` holds the enabled carriers (in the configured order), decides prices and checks a
  chosen machine. Enabled carriers come from config:
  ```properties
  app.shipping.enabled=omniva          # later: omniva,smartpost,dpd,unisend
  ```
- Pickup point lists are fetched from each carrier's public location feed and **cached** (refresh
  daily or so). Never fetch them on every request.

### API (built; also in the contract comment in `frontend/src/mocks/handlers.ts`)

```
GET /api/shipping/methods?country=EE                      -> [{ carrierCode, name, price: Money }]  priced for this session's cart
GET /api/shipping/pickup-points?carrier=omniva&country=EE -> PickupPoint[]  sorted by city, then name
```

## Omniva (`com.webshop.shipping.omniva`)

- Source: the public list https://www.omniva.ee/locations.json (about 1,500 entries, 900 KB), downloaded on
  first use and reused for 12 h (`app.shipping.omniva.refresh`). If a refresh fails, the previous list is kept.
  If the list was never loaded, the API answers 503.
- Kept: `TYPE "0"` (parcel machines) in EE/LV/LT. Dropped: post offices (`TYPE "1"`), the virtual
  "preference" entries ("1. eelistus" with no county, "2. Picapac" whose city and street are its own name) and the company-internal machine ("ettevõttesisene").
- Mapping: `ZIP` → id. `NAME` → name. `A5_NAME` + `A7_NAME` → address (the village from `A3_NAME` when there is
  no street). City: Estonia `A2_NAME`, Latvia/Lithuania `A3_NAME` → `A2_NAME` → `A1_NAME`. Coordinates:
  `Y_COORDINATE` is latitude, `X_COORDINATE` is longitude.
- Tests use real sample entries in `backend/src/test/resources/omniva/locations-sample.json` and never download the live list.
- Not built yet: registering shipments and printing labels (Omniva's business API needs a contract).

The order request carries `{ carrierCode, pickupPointId }` instead of (or alongside) the street
address. The backend checks that the pickup point exists for that carrier and recalculates the
shipping price itself. **Never accept a shipping price from the client.**

### Frontend

- Carrier list and parcel machine list come from the API, so carriers are not hardcoded.
- The parcel machine picker must be searchable (by city / name), work with a keyboard and work on phones.
- Store the chosen pickup point name and address on the order (a snapshot), so later changes to
  the carrier's list don't change past orders.
