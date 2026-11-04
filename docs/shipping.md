# Shipping and parcel machines

**Status:** planned. Today the checkout form (`frontend/src/features/checkout/types.ts`) asks for a
street address (`addressLine1, city, postalCode, country`) in `EE, FI, LV, LT, SE, DE`. The mock
charges €4.99 shipping, free from €50 (`SHIPPING_FEE`, `FREE_SHIPPING_FROM` in `handlers.ts`).

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

    /** Parcel machines for a country. Cache this: carriers publish lists that change rarely. */
    List<PickupPoint> pickupPoints(String countryCode);

    /** Price for this cart (server-side only). */
    Money quote(ShippingQuoteRequest request);

    /** Later: register the shipment with the carrier and get a tracking number / label. */
    default Optional<Shipment> createShipment(Order order) { return Optional.empty(); }
}
```

- `PickupPoint`: `{ id, carrierCode, name, address, city, postalCode, countryCode, lat?, lng? }`
- `ShippingCarrierRegistry` looks carriers up by code, and the enabled ones come from config:
  ```properties
  app.shipping.enabled=omniva,smartpost,dpd,unisend
  ```
- Pickup point lists are fetched from each carrier's public location feed and **cached** (refresh
  daily or so). Never fetch them on every request.

### API (proposed; add to the contract in `handlers.ts` when built)

```
GET /api/shipping/methods?country=EE         -> [{ carrierCode, name, price: Money }]
GET /api/shipping/pickup-points?carrier=omniva&country=EE  -> PickupPoint[]
```

The order request carries `{ carrierCode, pickupPointId }` instead of (or alongside) the street
address. The backend checks that the pickup point exists for that carrier and recalculates the
shipping price itself. **Never accept a shipping price from the client.**

### Frontend

- Carrier list and parcel machine list come from the API, so carriers are not hardcoded.
- The parcel machine picker must be searchable (by city / name), work with a keyboard and work on phones.
- Store the chosen pickup point name and address on the order (a snapshot), so later changes to
  the carrier's list don't change past orders.
