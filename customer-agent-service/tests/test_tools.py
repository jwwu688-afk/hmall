import httpx
import pytest

from app.contracts import ItemFilters
from app.java_client import JavaClient, JavaUnavailable


def test_catalog_request_uses_signed_token_and_bounded_filters():
    seen = {}

    def handler(request):
        seen["request"] = request
        return httpx.Response(200, json=[{"id": 3, "name": "测试商品", "price": 1299}])

    client = JavaClient("http://java", "signed-token", httpx.Client(transport=httpx.MockTransport(handler)))
    result = client.search_items(ItemFilters(key="鞋", page_size=99))
    assert result[0].id == 3
    assert result[0].price == 1299
    assert seen["request"].headers["authorization"] == "Bearer signed-token"
    assert seen["request"].url.params["pageSize"] == "20"


def test_guest_order_call_returns_unauthorized():
    client = JavaClient("http://java", "guest-token", httpx.Client(
        transport=httpx.MockTransport(lambda _: httpx.Response(401))))
    with pytest.raises(PermissionError):
        client.get_my_order(7)


def test_java_5xx_and_timeout_are_unavailable():
    server_error = JavaClient("http://java", "token", httpx.Client(
        transport=httpx.MockTransport(lambda _: httpx.Response(503))))
    with pytest.raises(JavaUnavailable):
        server_error.list_my_orders()

    def timeout(_):
        raise httpx.ReadTimeout("timeout")

    timed_out = JavaClient("http://java", "token", httpx.Client(transport=httpx.MockTransport(timeout)))
    with pytest.raises(JavaUnavailable):
        timed_out.list_my_orders()
