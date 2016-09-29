// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_address_builder.h"
#include "sparse_tensor_address_decoder.h"

namespace vespalib {
namespace tensor {


/**
 * This class transforms serialized sparse tensor addresses by padding
 * in "undefined" labels for new dimensions.
 */
class SparseTensorAddressPadder : public SparseTensorAddressBuilder
{
    enum class PadOp
    {
        PAD,
        COPY
    };

    std::vector<PadOp> _padOps;

public:
    SparseTensorAddressPadder(const TensorDimensions &resultDims,
                                 const TensorDimensions &inputDims)
        : SparseTensorAddressBuilder(),
          _padOps()
    {
        auto resultDimsItr = resultDims.cbegin();
        auto resultDimsItrEnd = resultDims.cend();
        for (auto &dim : inputDims) {
            while (resultDimsItr != resultDimsItrEnd && *resultDimsItr < dim) {
                _padOps.push_back(PadOp::PAD);
                ++resultDimsItr;
            }
            assert(resultDimsItr != resultDimsItrEnd && *resultDimsItr == dim);
            _padOps.push_back(PadOp::COPY);
            ++resultDimsItr;
        }
        while (resultDimsItr != resultDimsItrEnd) {
            _padOps.push_back(PadOp::PAD);
            ++resultDimsItr;
        }
    }

    void
    padAddress(SparseTensorAddressRef ref)
    {
        clear();
        SparseTensorAddressDecoder addr(ref);
        for (auto op : _padOps) {
            switch (op) {
            case PadOp::PAD:
                addUndefined();
                break;
            default:
                add(addr.decodeLabel());
            }
        }
        assert(!addr.valid());
    }
};


} // namespace vespalib::tensor
} // namespace vespalib