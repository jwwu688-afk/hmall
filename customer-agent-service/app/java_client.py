import httpx

from app.contracts import ItemCard, ItemFilters, LogisticsCard, OrderCard


class JavaUnavailable(RuntimeError):
    """商城业务接口暂时不可用。"""


class JavaClient:
    def __init__(self, base_url: str, delegation_token: str, client: httpx.Client | None = None):
        self.base_url = base_url.rstrip("/")
        self.delegation_token = delegation_token
        self.client = client or httpx.Client(timeout=5.0)

    def _get(self, path: str, params: dict | None = None):
        try:
            response = self.client.get(
                self.base_url + "/internal/customer" + path,
                params=params,
                headers={"Authorization": "Bearer " + self.delegation_token},
            )
        except httpx.RequestError as exc:
            raise JavaUnavailable("商城查询服务暂时不可用，请稍后重试") from exc
        if response.status_code in (401, 403):
            raise PermissionError("当前会话无权查询这项信息")
        if response.status_code == 404:
            return None
        if response.status_code >= 500:
            raise JavaUnavailable("商城查询服务暂时不可用，请稍后重试")
        response.raise_for_status()
        return response.json()

    def search_items(self, filters: ItemFilters) -> list[ItemCard]:
        rows = self._get("/items", {
            "key": filters.key, "brand": filters.brand, "category": filters.category,
            "minPrice": filters.min_price, "maxPrice": filters.max_price,
            "pageSize": max(1, min(20, filters.page_size)),
        }) or []
        return [self._item(row) for row in rows[:20]]

    def get_item(self, item_id: int) -> ItemCard | None:
        row = self._get(f"/items/{int(item_id)}")
        return self._item(row) if row else None

    def list_my_orders(self) -> list[OrderCard]:
        rows = self._get("/orders", {"pageSize": 20}) or []
        return [self._order(row) for row in rows[:20]]

    def get_my_order(self, order_id: int) -> OrderCard | None:
        row = self._get(f"/orders/{int(order_id)}")
        return self._order(row) if row else None

    def get_my_logistics(self, order_id: int) -> LogisticsCard | None:
        row = self._get(f"/orders/{int(order_id)}/logistics")
        if row is None:
            return None
        return LogisticsCard(order_id=row["orderId"], order_status=row["orderStatus"],
                             logistics_company=row.get("logisticsCompany"),
                             logistics_number=row.get("logisticsNumber"),
                             consign_time=row.get("consignTime"))

    @staticmethod
    def _item(row: dict) -> ItemCard:
        return ItemCard(id=row["id"], name=row["name"], price=row["price"],
                        stock=row.get("stock"), brand=row.get("brand"),
                        category=row.get("category"), spec=row.get("spec"), image=row.get("image"))

    @staticmethod
    def _order(row: dict) -> OrderCard:
        return OrderCard(id=row["id"], status=row["status"], total_fee=row.get("totalFee"),
                         details=row.get("details"), create_time=row.get("createTime"),
                         consign_time=row.get("consignTime"))
