package android.os


class Bundle:Parcelable {

    var map= HashMap<String, Any>()
    operator fun get(key: String): Any? {
        return map[key]
    }

    fun putString(key: String, value: String) {
        map[key] = value
    }

    fun putDouble(key: String, value: Double) {
        map[key] = value
    }

    fun putLong(key: String, value: Long) {
        map[key] = value
    }

    fun putInt(key: String, value: Int) {
        map[key] = value
    }

    fun putParcelableArray(key: String, value: Array<Parcelable?>) {
        map[key] = value
    }

    fun getDouble(key: String): Double {
        return map[key] as Double
    }

    fun getString(key: String): String? {
        return map[key] as String?
    }

    fun size(): Int {
        return map.keys.size
    }

    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel?, flags: Int) {}
}
