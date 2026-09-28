from dataclasses import dataclass


@dataclass(frozen=True)
class ItemFilters:
    key: str | None = None
    brand: str | None = None
    category: str | None = None
    min_price: int | None = None
    max_price: int | None = None
    page_size: int = 10


@dataclass(frozen=True)
class ItemCard:
    id: int
    name: str
    price: int
    stock: int | None = None
    brand: str | None = None
    category: str | None = None
    spec: str | None = None
    image: str | None = None


@dataclass(frozen=True)
class OrderCard:
    id: int
    status: int
    total_fee: int | None = None
    details: list[dict] | None = None
    create_time: str | None = None
    consign_time: str | None = None


@dataclass(frozen=True)
class LogisticsCard:
    order_id: int
    order_status: int
    logistics_company: str | None = None
    logistics_number: str | None = None
    consign_time: str | None = None
