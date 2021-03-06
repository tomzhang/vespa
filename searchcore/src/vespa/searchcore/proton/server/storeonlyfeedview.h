// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fileconfigmanager.h"
#include "ifeedview.h"
#include "isummaryadapter.h"
#include "replaypacketdispatcher.h"
#include "searchcontext.h"
#include "tlcproxy.h"
#include "pendinglidtracker.h"
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/common/feeddebugger.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/searchcore/proton/feedoperation/feedoperation.h>
#include <vespa/searchcore/proton/persistenceengine/resulthandler.h>
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/vespalib/util/threadstackexecutorbase.h>
#include <future>

namespace search { class IDestructorCallback; }

namespace proton {

class IReplayConfig;
class PerDocTypeFeedMetrics;
class ForceCommitContext;
class OperationDoneContext;
class PutDoneContext;
class RemoveDoneContext;
class CommitTimeTracker;

namespace documentmetastore { class ILidReuseDelayer; }

/**
 * The feed view used by the store-only sub database.
 *
 * Handles inserting/updating/removing of documents to the underlying document store.
 */
class StoreOnlyFeedView : public IFeedView,
                          protected FeedDebugger
{
protected:
    typedef search::transactionlog::Packet Packet;
public:
    using UP = std::unique_ptr<StoreOnlyFeedView>;
    using SP = std::shared_ptr<StoreOnlyFeedView>;
    using SerialNum = search::SerialNum;
    using LidVector = LidVectorContext::LidVector;
    using Document = document::Document;
    using DocumentUpdate = document::DocumentUpdate;
    using OnWriteDoneType =const std::shared_ptr<search::IDestructorCallback> &;
    using OnForceCommitDoneType =const std::shared_ptr<ForceCommitContext> &;
    using OnOperationDoneType = const std::shared_ptr<OperationDoneContext> &;
    using OnPutDoneType = const std::shared_ptr<PutDoneContext> &;
    using OnRemoveDoneType = const std::shared_ptr<RemoveDoneContext> &;
    using FeedTokenUP = std::unique_ptr<FeedToken>;
    using FutureDoc = std::future<Document::UP>;
    using PromisedDoc = std::promise<Document::UP>;
    using FutureStream = std::future<vespalib::nbostream>;
    using PromisedStream = std::promise<vespalib::nbostream>;
    using Lid = search::DocumentIdT;

    struct Context
    {
        const ISummaryAdapter::SP               &_summaryAdapter;
        const search::index::Schema::SP         &_schema;
        const IDocumentMetaStoreContext::SP     &_documentMetaStoreContext;
        const document::DocumentTypeRepo::SP    &_repo;
        searchcorespi::index::IThreadingService &_writeService;
        documentmetastore::ILidReuseDelayer     &_lidReuseDelayer;
        CommitTimeTracker                       &_commitTimeTracker;

        Context(const ISummaryAdapter::SP &summaryAdapter,
                const search::index::Schema::SP &schema,
                const IDocumentMetaStoreContext::SP &documentMetaStoreContext,
                const document::DocumentTypeRepo::SP &repo,
                searchcorespi::index::IThreadingService &writeService,
                documentmetastore::ILidReuseDelayer &lidReuseDelayer,
                CommitTimeTracker &commitTimeTracker)
            : _summaryAdapter(summaryAdapter),
              _schema(schema),
              _documentMetaStoreContext(documentMetaStoreContext),
              _repo(repo),
              _writeService(writeService),
              _lidReuseDelayer(lidReuseDelayer),
              _commitTimeTracker(commitTimeTracker)
        {}
    };

    struct PersistentParams
    {
        const SerialNum        _flushedDocumentMetaStoreSerialNum;
        const SerialNum        _flushedDocumentStoreSerialNum;
        const DocTypeName      _docTypeName;
        PerDocTypeFeedMetrics &_metrics;
        const uint32_t         _subDbId;
        const SubDbType        _subDbType;

        PersistentParams(SerialNum flushedDocumentMetaStoreSerialNum,
                         SerialNum flushedDocumentStoreSerialNum,
                         const DocTypeName &docTypeName,
                         PerDocTypeFeedMetrics &metrics,
                         uint32_t subDbId,
                         SubDbType subDbType)
            : _flushedDocumentMetaStoreSerialNum(flushedDocumentMetaStoreSerialNum),
              _flushedDocumentStoreSerialNum(flushedDocumentStoreSerialNum),
              _docTypeName(docTypeName),
              _metrics(metrics),
              _subDbId(subDbId),
              _subDbType(subDbType)
        {}
    };

protected:
    struct UpdateScope
    {
        bool _indexedFields;
        bool _nonAttributeFields;

        UpdateScope()
            : _indexedFields(false),
              _nonAttributeFields(false)
        {}
        bool hasIndexOrNonAttributeFields() const {
            return _indexedFields || _nonAttributeFields;
        }
    };

private:
    const ISummaryAdapter::SP                _summaryAdapter;
    const IDocumentMetaStoreContext::SP      _documentMetaStoreContext;
    const document::DocumentTypeRepo::SP     _repo;
    const document::DocumentType            *_docType;
    documentmetastore::ILidReuseDelayer     &_lidReuseDelayer;
    CommitTimeTracker                       &_commitTimeTracker;
    PendingLidTracker                        _pendingLidTracker;

protected:
    const search::index::Schema::SP          _schema;
    searchcorespi::index::IThreadingService &_writeService;
    PersistentParams                         _params;
    IDocumentMetaStore                      &_metaStore;

private:
    searchcorespi::index::IThreadService & summaryExecutor() {
        return _writeService.summary();
    }
    void putSummary(SerialNum serialNum,  Lid lid, FutureStream doc, OnOperationDoneType onDone);
    void putSummary(SerialNum serialNum,  Lid lid, Document::SP doc, OnOperationDoneType onDone);
    void removeSummary(SerialNum serialNum,  Lid lid);
    void heartBeatSummary(SerialNum serialNum);


    bool useDocumentStore(SerialNum replaySerialNum) const {
        return replaySerialNum > _params._flushedDocumentStoreSerialNum;
    }
    bool useDocumentMetaStore(SerialNum replaySerialNum) const {
        return replaySerialNum > _params._flushedDocumentMetaStoreSerialNum;
    }

    void adjustMetaStore(const DocumentOperation &op, const document::DocumentId &docId);
    void internalPut(FeedTokenUP token, const PutOperation &putOp);
    void internalUpdate(FeedTokenUP token, const UpdateOperation &updOp);

    bool lookupDocId(const document::DocumentId &gid, Lid & lid) const;
    void internalRemove(FeedTokenUP token, const RemoveOperation &rmOp);

    // Removes documents from meta store and document store.
    // returns the number of documents removed.
    size_t removeDocuments(const RemoveDocumentsOperation &op, bool remove_index_and_attribute_fields,
                           bool immediateCommit);

    void internalRemove(FeedTokenUP token, SerialNum serialNum, Lid lid,
                        FeedOperation::Type opType, std::shared_ptr<search::IDestructorCallback> moveDoneCtx);

    // Ack token early if visibility delay is nonzero
    void considerEarlyAck(FeedTokenUP &token, FeedOperation::Type opType);

    virtual void notifyGidToLidChange(const document::GlobalId &gid, uint32_t lid);

    void makeUpdatedDocument(SerialNum serialNum, Lid lid, DocumentUpdate::SP upd,
            OnOperationDoneType onWriteDone,PromisedDoc promisedDoc, PromisedStream promisedStream);

protected:
    virtual void internalDeleteBucket(const DeleteBucketOperation &delOp);
    virtual void heartBeatIndexedFields(SerialNum serialNum);
    virtual void heartBeatAttributes(SerialNum serialNum);

private:
    virtual void putAttributes(SerialNum serialNum, Lid lid, const Document &doc,
                               bool immediateCommit, OnPutDoneType onWriteDone);

    virtual void putIndexedFields(SerialNum serialNum, Lid lid, const Document::SP &newDoc,
                                  bool immediateCommit, OnOperationDoneType onWriteDone);

    virtual UpdateScope getUpdateScope(const DocumentUpdate &upd);

    virtual void updateAttributes(SerialNum serialNum, Lid lid, const DocumentUpdate &upd,
                                  bool immediateCommit, OnOperationDoneType onWriteDone);

    virtual void updateIndexedFields(SerialNum serialNum, Lid lid, FutureDoc doc,
                                     bool immediateCommit, OnOperationDoneType onWriteDone);

    virtual void removeAttributes(SerialNum serialNum, Lid lid, bool immediateCommit, OnRemoveDoneType onWriteDone);
    virtual void removeIndexedFields(SerialNum serialNum, Lid lid, bool immediateCommit, OnRemoveDoneType onWriteDone);

protected:
    virtual void removeAttributes(SerialNum serialNum, const LidVector &lidsToRemove,
                                  bool immediateCommit, OnWriteDoneType onWriteDone);

    virtual void removeIndexedFields(SerialNum serialNum, const LidVector &lidsToRemove,
                                     bool immediateCommit, OnWriteDoneType onWriteDone);

public:
    StoreOnlyFeedView(const Context &ctx, const PersistentParams &params);

    virtual ~StoreOnlyFeedView() {}

    const ISummaryAdapter::SP &getSummaryAdapter() const { return _summaryAdapter; }
    const search::index::Schema::SP &getSchema() const { return _schema; }
    const PersistentParams &getPersistentParams() const { return _params; }
    const search::IDocumentStore &getDocumentStore() const { return _summaryAdapter->getDocumentStore(); }
    const IDocumentMetaStoreContext::SP &getDocumentMetaStore() const { return _documentMetaStoreContext; }
    searchcorespi::index::IThreadingService &getWriteService() { return _writeService; }
    documentmetastore::ILidReuseDelayer &getLidReuseDelayer() { return _lidReuseDelayer; }
    CommitTimeTracker &getCommitTimeTracker() { return _commitTimeTracker; }

    /**
     * Implements IFeedView.
     */
    virtual const document::DocumentTypeRepo::SP &getDocumentTypeRepo() const override { return _repo; }
    virtual const ISimpleDocumentMetaStore *getDocumentMetaStorePtr() const override;

    /**
     * Similar to IPersistenceHandler functions.
     * Takes pointer to feed token instead of instance because
     * when replaying the spooler we don't have a feed token.
     */

    virtual void preparePut(PutOperation &putOp) override;
    virtual void handlePut(FeedToken *token, const PutOperation &putOp) override;
    virtual void prepareUpdate(UpdateOperation &updOp) override;
    virtual void handleUpdate(FeedToken *token, const UpdateOperation &updOp) override;
    virtual void prepareRemove(RemoveOperation &rmOp) override;
    virtual void handleRemove(FeedToken *token, const RemoveOperation &rmOp) override;
    virtual void prepareDeleteBucket(DeleteBucketOperation &delOp) override;
    virtual void handleDeleteBucket(const DeleteBucketOperation &delOp) override;
    virtual void prepareMove(MoveOperation &putOp) override;
    virtual void handleMove(const MoveOperation &putOp, std::shared_ptr<search::IDestructorCallback> doneCtx) override;
    virtual void heartBeat(search::SerialNum serialNum) override;
    virtual void sync() override;
    virtual void forceCommit(SerialNum serialNum) override;
    virtual void forceCommit(SerialNum serialNum, OnForceCommitDoneType onCommitDone);

    /**
     * Prune lids present in operation.  Caller must call doneSegment()
     * on prune operation after this call.
     *
     * Called by writer thread.
     */
    virtual void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &pruneOp) override;
    virtual void handleCompactLidSpace(const CompactLidSpaceOperation &op) override;
};

}
