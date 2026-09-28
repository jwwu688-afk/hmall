from dataclasses import asdict

from langchain_core.tools import tool

from app.contracts import ItemFilters
from app.java_client import JavaClient, JavaUnavailable


def make_tools(client: JavaClient, authenticated: bool):
    @tool("search_items")
    def search_items(key: str = "", brand: str = "", category: str = "", page_size: int = 10) -> dict:
        """按名称、品牌或类目搜索当前在售商品，价格单位为分。"""
        try:
            return {"items": [asdict(row) for row in client.search_items(ItemFilters(
                key=key or None, brand=brand or None, category=category or None, page_size=page_size))]}
        except JavaUnavailable as exc:
            return {"error": str(exc)}

    @tool("get_item")
    def get_item(item_id: int) -> dict:
        """按商品 ID 查询当前在售商品详情。"""
        try:
            row = client.get_item(item_id)
            return {"item": asdict(row) if row else None}
        except JavaUnavailable as exc:
            return {"error": str(exc)}

    public_tools = [search_items, get_item]
    if not authenticated:
        return public_tools

    @tool("list_my_orders")
    def list_my_orders() -> dict:
        """查询当前已登录买家自己的最近订单。"""
        try:
            return {"orders": [asdict(row) for row in client.list_my_orders()]}
        except (JavaUnavailable, PermissionError) as exc:
            return {"error": str(exc)}

    @tool("get_my_order")
    def get_my_order(order_id: int) -> dict:
        """查询当前已登录买家的指定订单，不能选择用户 ID。"""
        try:
            row = client.get_my_order(order_id)
            return {"order": asdict(row) if row else None}
        except (JavaUnavailable, PermissionError) as exc:
            return {"error": str(exc)}

    @tool("get_my_logistics")
    def get_my_logistics(order_id: int) -> dict:
        """查询本人订单的发货状态、物流公司和单号；没有物流节点或预计送达时间。"""
        try:
            row = client.get_my_logistics(order_id)
            return {"logistics": asdict(row) if row else None}
        except (JavaUnavailable, PermissionError) as exc:
            return {"error": str(exc)}

    return public_tools + [list_my_orders, get_my_order, get_my_logistics]
