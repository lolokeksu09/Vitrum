package app.rayclient

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class SecurityTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @Test fun cryptRoundtripAndTamper() {
        val plain = """[{"link":"vless://11111111-2222-3333-4444-555555555555@example.com:443?pbk=SECRET"}]"""
        val a = Crypt.encrypt(key, plain); val b = Crypt.encrypt(key, plain)
        assertTrue(a.startsWith("v1:")); assertFalse("секрет не должен быть виден", a.contains("SECRET") || a.contains("vless"))
        assertNotEquals("вектор инициализации у каждой записи свой", a, b)
        assertEquals(plain, Crypt.decrypt(key, a)); assertEquals(plain, Crypt.decrypt(key, b))
        val tampered = a.dropLast(4) + (if (a.takeLast(4) == "AAAA") "BBBB" else "AAAA")
        assertNull("подмена данных должна ломать расшифровку", Crypt.decrypt(key, tampered))
        assertNull(Crypt.decrypt(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey(), a))   // чужой ключ
        assertNull(Crypt.decrypt(key, "[]")); assertNull(Crypt.decrypt(key, "v1:xx"))
    }
    @Test fun unreadableDataIsNotReplacedByEmptyList() {
        val a = Crypt.encrypt(key, "[1]")
        assertEquals("старые данные без шифрования читаются как есть", "[]", Vault.openWith("[]") { null })
        assertEquals("[1]", Vault.openWith(a) { Crypt.decrypt(key, it) })
        assertNull("ключ не подошёл: null, а не пустой список, который затёр бы данные", Vault.openWith(a) { null })
        assertNull(Vault.openWith(a) { Crypt.decrypt(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey(), it) })
    }
    @Test fun healthStoresHashesNotLinks() {
        Health.clear()
        val link = "vless://secret-uuid@host.example:443?pbk=KEY#name"
        Health.add(link, true, 100); Health.add(link, true, 120); Health.add(link, false, 0)
        val json = Health.toJson()
        assertFalse(json.contains("secret-uuid") || json.contains("vless://") || json.contains("KEY"))
        assertEquals(3, Health.stats(link)!!.n)
        Health.clear(); Health.fromJson("""{"vless://old@h:1":[[${System.currentTimeMillis()},1,50]]}""")   // старый формат
        assertEquals(1, Health.stats("vless://old@h:1")!!.n); assertFalse(Health.toJson().contains("vless://"))
    }
}
