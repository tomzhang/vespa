// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::SerializableArray
 * \brief key/value array that can be serialized and deserialized efficiently.
 *
 * The SerializableArray class is optimized for doing multiple
 * serialize()/deserialize() without changing attributes. Once
 * an attribute is changed, serialization is much slower. This makes
 * sense, since a document travels between a lot of processes and
 * queues, where nothing happens except serialization and deserialization.
 *
 * It also supports multiple deserializations, where serializations
 * from multiple other arrays are merged into one array.
 * Attributes that overlap Get the last known value.
 */

#pragma once

#include <vespa/document/util/compressionconfig.h>
#include <vespa/vespalib/objects/cloneable.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/util/memory.h>
#include <vector>

#define VESPA_DLL_LOCAL  __attribute__ ((visibility("hidden")))

namespace document {

class SerializableArrayIterator;
class ByteBuffer;

namespace serializablearray {
    class BufferMap;
}

class SerializableArray : public vespalib::Cloneable
{
public:
        // Counts set during serialization, in order to provide metrics for how
        // often we use cached version, and how often we compress.
    struct Statistics {
        uint64_t _usedCachedSerializationCount;
        uint64_t _compressedDocumentCount;
        uint64_t _compressionDidntHelpCount;
        uint64_t _uncompressableCount;
        uint64_t _serializedUncompressed;
        uint64_t _inputWronglySerialized;

        Statistics()
            : _usedCachedSerializationCount(0),
              _compressedDocumentCount(0),
              _compressionDidntHelpCount(0),
              _uncompressableCount(0),
              _serializedUncompressed(0),
              _inputWronglySerialized(0) {}
    };

    /**
     * Contains the id of a field, the size and a buffer reference that is either
     * a relative offset to a common buffer, or the buffer itself it it is not.
     * The most significant bit of the _sz member indicates which of the 2 it is.
     */
    class Entry {
    public:
        Entry() : _id(0), _sz(0), _data() {}
        Entry(int i) : _id(i), _sz(0), _data()  {}
        Entry(uint32_t i, uint32_t sz, uint32_t off) : _id(i), _sz(sz), _data(off) {}
        Entry(uint32_t i, uint32_t sz, const char * buf) : _id(i), _sz(sz | BUFFER_MASK), _data(buf) {}

        int32_t id() const { return _id; }
        uint32_t size() const { return _sz & ~BUFFER_MASK; }
        bool hasBuffer() const { return (_sz & BUFFER_MASK); }
        bool operator < (const Entry & e) const { return cmp(e) < 0; }
        int cmp(const Entry & e) const { return _id - e._id; }
        void setBuffer(const char * buffer) { _data._buffer = buffer; _sz |= BUFFER_MASK; }
        VESPA_DLL_LOCAL const char * getBuffer(const ByteBuffer * readOnlyBuffer) const;
    private:
        uint32_t getOffset() const { return _data._offset; }
        enum { BUFFER_MASK=0x80000000 };
        int32_t      _id;
        uint32_t     _sz;
        union Data {
           Data() : _buffer(0) { }
           Data(const char * buffer) : _buffer(buffer) { }
           Data(uint32_t offset) : _offset(offset) { }
           const char * _buffer;
           uint32_t     _offset;
        } _data;
    };
    class EntryMap : public std::vector<Entry>
    {
    private:
        using V=std::vector<Entry>;
    public:
        EntryMap() : V() { }
    };

    static const uint32_t ReservedId = 100;
    static const uint32_t ReservedIdUpper = 128;


private:
    static Statistics _stats;

public:
    static Statistics& getStatistics() { return _stats; }
    using CP = vespalib::CloneablePtr<SerializableArray>;
    using UP = std::unique_ptr<SerializableArray>;
    using ByteBufferUP = std::unique_ptr<ByteBuffer>;

    SerializableArray();
    virtual ~SerializableArray();

    void swap(SerializableArray& other);

    /**
     * Stores a value in the array.
     *
     * @param id The ID to associate the value with.
     * @param value The value to store.
     * @param len The length of the buffer.
     */
    void set(int id, const char* value, int len);

    /** Stores a value in the array. */
    void set(int id, ByteBufferUP buffer);

    /**
     * Gets a value from the array. This is the faster version of the above.
     * It will just give you the pointers needed. No refcounting or anything.
     *
     * @param id The ID of the value to Get.
     *
     * @return Returns a reference to a buffer. c_str and size will be zero if
     * none is found.
     */
    vespalib::ConstBufferRef get(int id) const;

    /** @return Returns true if the given ID is Set in the array. */
    bool has(int id) const;

    /** @return Number of elements in array */
    bool hasAnyElems() const { return !_entries.empty(); }

    /**
     * clears an attribute.
     *
     * @param id The ID of the attribute to remove from the array.
     */
    void clear(int id);

    /** Deletes all stored attributes. */
    void clear();

    CompressionConfig::Type getCompression() const { return _serializedCompression; }
    CompressionInfo getCompressionInfo() const;

    /**
     * Sets the serialized data that is the basis for this object's
     * content. This is used by deserialization. Any existing entries
     * are cleared.
     */
    void assign(EntryMap &entries,
                ByteBufferUP buffer,
                CompressionConfig::Type comp_type,
                uint32_t uncompressed_length);

    bool empty() const { return _entries.empty(); }

    const ByteBuffer* getSerializedBuffer() const {
        return CompressionConfig::isCompressed(_serializedCompression)
            ? _compSerData.get()
            : _uncompSerData.get();
    }

    SerializableArray* clone() const override { return new SerializableArray(*this); }
    SerializableArray(const SerializableArray&); // Public only for test
    SerializableArray& operator=(const SerializableArray&) = delete;
    const EntryMap & getEntries() const { return _entries; }
private:
    bool shouldDecompress() const {
        return _compSerData.get() && !_uncompSerData.get();
    }
    bool maybeDecompressAndCatch() const {
        if ( shouldDecompress() ) {
            return deCompressAndCatch();
        }
        return false;
    }

    bool deCompressAndCatch() const;
    void maybeDecompress() const {
        if ( shouldDecompress() ) {
            const_cast<SerializableArray *>(this)->deCompress();
        }
    }
    void deCompress(); // throw (DeserializeException);

    /** Contains the stored attributes, with reference to the real data.. */
    EntryMap _entries;
    /** The buffers we own. */
    std::unique_ptr<serializablearray::BufferMap> _owned;

    /** Data we deserialized from, if applicable. */
    ByteBufferUP             _uncompSerData;
    ByteBufferUP             _compSerData;
    CompressionConfig::Type  _serializedCompression;

    uint32_t     _uncompressedLength;

    VESPA_DLL_LOCAL void invalidate();
    VESPA_DLL_LOCAL EntryMap::const_iterator find(int id) const;
    VESPA_DLL_LOCAL EntryMap::iterator find(int id);
};

} // document
