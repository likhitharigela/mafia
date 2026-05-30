import pytest

from app.services.game_orchestrator import room_store


@pytest.fixture(autouse=True)
def reset_store():
    room_store.reset()
    yield


class TestRoomStore:
    def test_register_session_marks_user_present(self):
        assert room_store._SESSIONS.get("user1") is None

        room_store.register_session("user1")

        assert room_store._SESSIONS.get("user1") is True

    def test_register_session_multiple_users(self):
        room_store.register_session("user1")
        room_store.register_session("user2")

        assert room_store._SESSIONS["user1"] is True
        assert room_store._SESSIONS["user2"] is True

    def test_cache_and_get_room_case_insensitive_code(self):
        room_store.cache_room("CODE1", "room-1", "host1")

        assert room_store.get_room_id("code1") == "room-1"
        assert room_store.get_room_id("CODE1") == "room-1"
        assert room_store.get_room_id("CoDe1") == "room-1"
        assert room_store.get_host("room-1") == "host1"

    def test_cache_room_uppercases_code(self):
        room_store.cache_room("AbCdEf", "room-abc", "host-abc")

        assert "ABCDEF" in room_store._CODE_TO_ID
        assert room_store._CODE_TO_ID["ABCDEF"] == "room-abc"

    def test_cache_room_updates_existing_room(self):
        room_store.cache_room("CODE1", "room-1", "host1")
        room_store.cache_room("code1", "room-2", "host2")

        assert room_store.get_room_id("CODE1") == "room-2"
        assert room_store.get_host("room-2") == "host2"
        # Host mapping for old room id remains in _ROOM_HOSTS unless cleared
        assert room_store.get_host("room-1") == "host1"

    def test_get_room_id_returns_none_for_unknown_code(self):
        assert room_store.get_room_id("missing") is None

    def test_get_host_returns_none_for_unknown_room(self):
        assert room_store.get_host("missing-room") is None

    def test_is_host_checks_room_owner(self):
        room_store.cache_room("CODE1", "room-1", "host1")

        assert room_store.is_host("room-1", "host1") is True
        assert room_store.is_host("room-1", "player1") is False
        assert room_store.is_host("unknown-room", "host1") is False

    def test_reset_clears_all_stores(self):
        room_store.register_session("user1")
        room_store.cache_room("CODE1", "room-1", "host1")

        room_store.reset()

        assert room_store._SESSIONS == {}
        assert room_store._CODE_TO_ID == {}
        assert room_store._ROOM_HOSTS == {}

    def test_reset_allows_reusing_room_code(self):
        room_store.cache_room("CODE1", "room-1", "host1")
        room_store.reset()
        room_store.cache_room("CODE1", "room-2", "host2")

        assert room_store.get_room_id("CODE1") == "room-2"
        assert room_store.get_host("room-2") == "host2"

    def test_register_session_does_not_remove_on_own(self):
        room_store.register_session("user1")
        room_store.register_session("user2")

        # No method to unregister, so sessions stay until reset
        assert room_store._SESSIONS["user1"] is True
        assert room_store._SESSIONS["user2"] is True

        room_store.reset()

        assert room_store._SESSIONS == {}