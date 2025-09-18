package com.musicinvestment.musicapp.contract;

import io.reactivex.Flowable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple6;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/LFDT-web3j/web3j/tree/main/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 1.7.0.
 */
@SuppressWarnings("rawtypes")
public class ArtistSharesToken extends Contract {
    public static final String BINARY = "Bin file was not provided";

    public static final String FUNC_ETH_USD_DECIMALS = "ETH_USD_DECIMALS";

    public static final String FUNC_MAX_CANDLES_15M = "MAX_CANDLES_15M";

    public static final String FUNC_MAX_CANDLES_1D = "MAX_CANDLES_1D";

    public static final String FUNC_MAX_CANDLES_1H = "MAX_CANDLES_1H";

    public static final String FUNC_MAX_CANDLES_1M = "MAX_CANDLES_1M";

    public static final String FUNC_MAX_CANDLES_4H = "MAX_CANDLES_4H";

    public static final String FUNC_MAX_CANDLES_5M = "MAX_CANDLES_5M";

    public static final String FUNC_PRICE_INCREASE_PER_TOKEN = "PRICE_INCREASE_PER_TOKEN";

    public static final String FUNC_TF_15MIN = "TF_15MIN";

    public static final String FUNC_TF_1DAY = "TF_1DAY";

    public static final String FUNC_TF_1H = "TF_1H";

    public static final String FUNC_TF_1MIN = "TF_1MIN";

    public static final String FUNC_TF_4H = "TF_4H";

    public static final String FUNC_TF_5MIN = "TF_5MIN";

    public static final String FUNC_ADJUSTDAILYLIMIT = "adjustDailyLimit";

    public static final String FUNC_AIRDROPSUPPLY = "airdropSupply";

    public static final String FUNC_ALLOWANCE = "allowance";

    public static final String FUNC_APPROVE = "approve";

    public static final String FUNC_BALANCEOF = "balanceOf";

    public static final String FUNC_BASEPRICE = "basePrice";

    public static final String FUNC_BUYLISTEDSHARES = "buyListedShares";

    public static final String FUNC_BUYSHARES = "buyShares";

    public static final String FUNC_CANDLESBYTIMEFRAME = "candlesByTimeframe";

    public static final String FUNC_CURRENTPRICE = "currentPrice";

    public static final String FUNC_DAILYLIMIT = "dailyLimit";

    public static final String FUNC_DAILYTRADES = "dailyTrades";

    public static final String FUNC_DECIMALS = "decimals";

    public static final String FUNC_DEPLOYER = "deployer";

    public static final String FUNC_DISTRIBUTEAIRDROP = "distributeAirdrop";

    public static final String FUNC_GETCANDLEHISTORY = "getCandleHistory";

    public static final String FUNC_GETCURRENTPRICE = "getCurrentPrice";

    public static final String FUNC_GETETHUSDPRICE = "getEthUsdPrice";

    public static final String FUNC_GETTOTALVOLUMETRADED = "getTotalVolumeTraded";

    public static final String FUNC_GETUSERS = "getUsers";

    public static final String FUNC_GLOBALDAILYTRADES = "globalDailyTrades";

    public static final String FUNC_HASRECEIVEDAIRDROP = "hasReceivedAirdrop";

    public static final String FUNC_ISUSER = "isUser";

    public static final String FUNC_LASTRECORDEDDAY = "lastRecordedDay";

    public static final String FUNC_MINTFORTRADING = "mintForTrading";

    public static final String FUNC_NAME = "name";

    public static final String FUNC_OWNER = "owner";

    public static final String FUNC_PLATFORMADDRESS = "platformAddress";

    public static final String FUNC_PLATFORMSUPPLY = "platformSupply";

    public static final String FUNC_PRICELIMIT = "priceLimit";

    public static final String FUNC_RENOUNCEOWNERSHIP = "renounceOwnership";

    public static final String FUNC_RESETDAILYLIMITS = "resetDailyLimits";

    public static final String FUNC_SELLSHARES = "sellShares";

    public static final String FUNC_SHARESFORSALE = "sharesForSale";

    public static final String FUNC_SYMBOL = "symbol";

    public static final String FUNC_TOTALSUPPLY = "totalSupply";

    public static final String FUNC_TOTALVOLUMETRADED = "totalVolumeTraded";

    public static final String FUNC_TRANSFER = "transfer";

    public static final String FUNC_TRANSFERFROM = "transferFrom";

    public static final String FUNC_TRANSFEROWNERSHIP = "transferOwnership";

    public static final String FUNC_USERTRADINGSUPPLY = "userTradingSupply";

    public static final String FUNC_USERS = "users";

    /* 
    public static final CustomError ERC20INSUFFICIENTALLOWANCE_ERROR = new CustomError("ERC20InsufficientAllowance", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final CustomError ERC20INSUFFICIENTBALANCE_ERROR = new CustomError("ERC20InsufficientBalance", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final CustomError ERC20INVALIDAPPROVER_ERROR = new CustomError("ERC20InvalidApprover", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    ;

    public static final CustomError ERC20INVALIDRECEIVER_ERROR = new CustomError("ERC20InvalidReceiver", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    ;

    public static final CustomError ERC20INVALIDSENDER_ERROR = new CustomError("ERC20InvalidSender", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    ;

    public static final CustomError ERC20INVALIDSPENDER_ERROR = new CustomError("ERC20InvalidSpender", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    ;

    public static final CustomError OWNABLEINVALIDOWNER_ERROR = new CustomError("OwnableInvalidOwner", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    ;

    public static final CustomError OWNABLEUNAUTHORIZEDACCOUNT_ERROR = new CustomError("OwnableUnauthorizedAccount", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    ;
    */

    public static final Event APPROVAL_EVENT = new Event("Approval", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event CANDLEUPDATED_EVENT = new Event("CandleUpdated", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event DAILYLIMITADJUSTED_EVENT = new Event("DailyLimitAdjusted", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event DEBUGLOG_EVENT = new Event("DebugLog", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event OWNERSHIPTRANSFERRED_EVENT = new Event("OwnershipTransferred", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}));
    ;

    public static final Event PRICEUPDATED_EVENT = new Event("PriceUpdated", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    ;

    public static final Event SHARESBOUGHT_EVENT = new Event("SharesBought", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event SHARESLISTED_EVENT = new Event("SharesListed", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>(true) {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event SHARESSOLD_EVENT = new Event("SharesSold", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event TRANSFER_EVENT = new Event("Transfer", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    @Deprecated
    protected ArtistSharesToken(String contractAddress, Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected ArtistSharesToken(String contractAddress, Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected ArtistSharesToken(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected ArtistSharesToken(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static List<ApprovalEventResponse> getApprovalEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(APPROVAL_EVENT, transactionReceipt);
        ArrayList<ApprovalEventResponse> responses = new ArrayList<ApprovalEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            ApprovalEventResponse typedResponse = new ApprovalEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.owner = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.spender = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static ApprovalEventResponse getApprovalEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(APPROVAL_EVENT, log);
        ApprovalEventResponse typedResponse = new ApprovalEventResponse();
        typedResponse.log = log;
        typedResponse.owner = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.spender = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<ApprovalEventResponse> approvalEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getApprovalEventFromLog(log));
    }

    public Flowable<ApprovalEventResponse> approvalEventFlowable(DefaultBlockParameter startBlock,
            DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(APPROVAL_EVENT));
        return approvalEventFlowable(filter);
    }

    public static List<CandleUpdatedEventResponse> getCandleUpdatedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(CANDLEUPDATED_EVENT, transactionReceipt);
        ArrayList<CandleUpdatedEventResponse> responses = new ArrayList<CandleUpdatedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            CandleUpdatedEventResponse typedResponse = new CandleUpdatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.timeframe = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.open = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.high = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
            typedResponse.low = (BigInteger) eventValues.getNonIndexedValues().get(4).getValue();
            typedResponse.close = (BigInteger) eventValues.getNonIndexedValues().get(5).getValue();
            typedResponse.volume = (BigInteger) eventValues.getNonIndexedValues().get(6).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static CandleUpdatedEventResponse getCandleUpdatedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(CANDLEUPDATED_EVENT, log);
        CandleUpdatedEventResponse typedResponse = new CandleUpdatedEventResponse();
        typedResponse.log = log;
        typedResponse.timeframe = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.open = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
        typedResponse.high = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
        typedResponse.low = (BigInteger) eventValues.getNonIndexedValues().get(4).getValue();
        typedResponse.close = (BigInteger) eventValues.getNonIndexedValues().get(5).getValue();
        typedResponse.volume = (BigInteger) eventValues.getNonIndexedValues().get(6).getValue();
        return typedResponse;
    }

    public Flowable<CandleUpdatedEventResponse> candleUpdatedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getCandleUpdatedEventFromLog(log));
    }

    public Flowable<CandleUpdatedEventResponse> candleUpdatedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(CANDLEUPDATED_EVENT));
        return candleUpdatedEventFlowable(filter);
    }

    public static List<DailyLimitAdjustedEventResponse> getDailyLimitAdjustedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(DAILYLIMITADJUSTED_EVENT, transactionReceipt);
        ArrayList<DailyLimitAdjustedEventResponse> responses = new ArrayList<DailyLimitAdjustedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DailyLimitAdjustedEventResponse typedResponse = new DailyLimitAdjustedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.newDailyLimit = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.newTargetDailyLimitUsd = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static DailyLimitAdjustedEventResponse getDailyLimitAdjustedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(DAILYLIMITADJUSTED_EVENT, log);
        DailyLimitAdjustedEventResponse typedResponse = new DailyLimitAdjustedEventResponse();
        typedResponse.log = log;
        typedResponse.newDailyLimit = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.newTargetDailyLimitUsd = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<DailyLimitAdjustedEventResponse> dailyLimitAdjustedEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getDailyLimitAdjustedEventFromLog(log));
    }

    public Flowable<DailyLimitAdjustedEventResponse> dailyLimitAdjustedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DAILYLIMITADJUSTED_EVENT));
        return dailyLimitAdjustedEventFlowable(filter);
    }

    public static List<DebugLogEventResponse> getDebugLogEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(DEBUGLOG_EVENT, transactionReceipt);
        ArrayList<DebugLogEventResponse> responses = new ArrayList<DebugLogEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DebugLogEventResponse typedResponse = new DebugLogEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.message = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.value1 = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.value2 = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.value3 = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static DebugLogEventResponse getDebugLogEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(DEBUGLOG_EVENT, log);
        DebugLogEventResponse typedResponse = new DebugLogEventResponse();
        typedResponse.log = log;
        typedResponse.message = (String) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.value1 = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.value2 = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
        typedResponse.value3 = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
        return typedResponse;
    }

    public Flowable<DebugLogEventResponse> debugLogEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getDebugLogEventFromLog(log));
    }

    public Flowable<DebugLogEventResponse> debugLogEventFlowable(DefaultBlockParameter startBlock,
            DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DEBUGLOG_EVENT));
        return debugLogEventFlowable(filter);
    }

    public static List<OwnershipTransferredEventResponse> getOwnershipTransferredEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(OWNERSHIPTRANSFERRED_EVENT, transactionReceipt);
        ArrayList<OwnershipTransferredEventResponse> responses = new ArrayList<OwnershipTransferredEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            OwnershipTransferredEventResponse typedResponse = new OwnershipTransferredEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.previousOwner = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.newOwner = (String) eventValues.getIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static OwnershipTransferredEventResponse getOwnershipTransferredEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(OWNERSHIPTRANSFERRED_EVENT, log);
        OwnershipTransferredEventResponse typedResponse = new OwnershipTransferredEventResponse();
        typedResponse.log = log;
        typedResponse.previousOwner = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.newOwner = (String) eventValues.getIndexedValues().get(1).getValue();
        return typedResponse;
    }

    public Flowable<OwnershipTransferredEventResponse> ownershipTransferredEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getOwnershipTransferredEventFromLog(log));
    }

    public Flowable<OwnershipTransferredEventResponse> ownershipTransferredEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(OWNERSHIPTRANSFERRED_EVENT));
        return ownershipTransferredEventFlowable(filter);
    }

    public static List<PriceUpdatedEventResponse> getPriceUpdatedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(PRICEUPDATED_EVENT, transactionReceipt);
        ArrayList<PriceUpdatedEventResponse> responses = new ArrayList<PriceUpdatedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            PriceUpdatedEventResponse typedResponse = new PriceUpdatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.newPrice = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static PriceUpdatedEventResponse getPriceUpdatedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(PRICEUPDATED_EVENT, log);
        PriceUpdatedEventResponse typedResponse = new PriceUpdatedEventResponse();
        typedResponse.log = log;
        typedResponse.newPrice = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<PriceUpdatedEventResponse> priceUpdatedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getPriceUpdatedEventFromLog(log));
    }

    public Flowable<PriceUpdatedEventResponse> priceUpdatedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(PRICEUPDATED_EVENT));
        return priceUpdatedEventFlowable(filter);
    }

    public static List<SharesBoughtEventResponse> getSharesBoughtEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(SHARESBOUGHT_EVENT, transactionReceipt);
        ArrayList<SharesBoughtEventResponse> responses = new ArrayList<SharesBoughtEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            SharesBoughtEventResponse typedResponse = new SharesBoughtEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.buyer = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.price = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static SharesBoughtEventResponse getSharesBoughtEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(SHARESBOUGHT_EVENT, log);
        SharesBoughtEventResponse typedResponse = new SharesBoughtEventResponse();
        typedResponse.log = log;
        typedResponse.buyer = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.price = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<SharesBoughtEventResponse> sharesBoughtEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getSharesBoughtEventFromLog(log));
    }

    public Flowable<SharesBoughtEventResponse> sharesBoughtEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(SHARESBOUGHT_EVENT));
        return sharesBoughtEventFlowable(filter);
    }

    public static List<SharesListedEventResponse> getSharesListedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(SHARESLISTED_EVENT, transactionReceipt);
        ArrayList<SharesListedEventResponse> responses = new ArrayList<SharesListedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            SharesListedEventResponse typedResponse = new SharesListedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.user = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.shareId = (BigInteger) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.price = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static SharesListedEventResponse getSharesListedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(SHARESLISTED_EVENT, log);
        SharesListedEventResponse typedResponse = new SharesListedEventResponse();
        typedResponse.log = log;
        typedResponse.user = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.shareId = (BigInteger) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.price = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<SharesListedEventResponse> sharesListedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getSharesListedEventFromLog(log));
    }

    public Flowable<SharesListedEventResponse> sharesListedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(SHARESLISTED_EVENT));
        return sharesListedEventFlowable(filter);
    }

    public static List<SharesSoldEventResponse> getSharesSoldEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(SHARESSOLD_EVENT, transactionReceipt);
        ArrayList<SharesSoldEventResponse> responses = new ArrayList<SharesSoldEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            SharesSoldEventResponse typedResponse = new SharesSoldEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.seller = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.price = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static SharesSoldEventResponse getSharesSoldEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(SHARESSOLD_EVENT, log);
        SharesSoldEventResponse typedResponse = new SharesSoldEventResponse();
        typedResponse.log = log;
        typedResponse.seller = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.price = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<SharesSoldEventResponse> sharesSoldEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getSharesSoldEventFromLog(log));
    }

    public Flowable<SharesSoldEventResponse> sharesSoldEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(SHARESSOLD_EVENT));
        return sharesSoldEventFlowable(filter);
    }

    public static List<TransferEventResponse> getTransferEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(TRANSFER_EVENT, transactionReceipt);
        ArrayList<TransferEventResponse> responses = new ArrayList<TransferEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            TransferEventResponse typedResponse = new TransferEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.from = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.to = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static TransferEventResponse getTransferEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(TRANSFER_EVENT, log);
        TransferEventResponse typedResponse = new TransferEventResponse();
        typedResponse.log = log;
        typedResponse.from = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.to = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<TransferEventResponse> transferEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getTransferEventFromLog(log));
    }

    public Flowable<TransferEventResponse> transferEventFlowable(DefaultBlockParameter startBlock,
            DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(TRANSFER_EVENT));
        return transferEventFlowable(filter);
    }

    public RemoteFunctionCall<BigInteger> ETH_USD_DECIMALS() {
        final Function function = new Function(FUNC_ETH_USD_DECIMALS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> MAX_CANDLES_15M() {
        final Function function = new Function(FUNC_MAX_CANDLES_15M, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> MAX_CANDLES_1D() {
        final Function function = new Function(FUNC_MAX_CANDLES_1D, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> MAX_CANDLES_1H() {
        final Function function = new Function(FUNC_MAX_CANDLES_1H, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> MAX_CANDLES_1M() {
        final Function function = new Function(FUNC_MAX_CANDLES_1M, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> MAX_CANDLES_4H() {
        final Function function = new Function(FUNC_MAX_CANDLES_4H, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> MAX_CANDLES_5M() {
        final Function function = new Function(FUNC_MAX_CANDLES_5M, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> PRICE_INCREASE_PER_TOKEN() {
        final Function function = new Function(FUNC_PRICE_INCREASE_PER_TOKEN, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> TF_15MIN() {
        final Function function = new Function(FUNC_TF_15MIN, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> TF_1DAY() {
        final Function function = new Function(FUNC_TF_1DAY, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> TF_1H() {
        final Function function = new Function(FUNC_TF_1H, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> TF_1MIN() {
        final Function function = new Function(FUNC_TF_1MIN, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> TF_4H() {
        final Function function = new Function(FUNC_TF_4H, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> TF_5MIN() {
        final Function function = new Function(FUNC_TF_5MIN, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> adjustDailyLimit(
            BigInteger newTargetDailyLimitUsd) {
        final Function function = new Function(
                FUNC_ADJUSTDAILYLIMIT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(newTargetDailyLimitUsd)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> airdropSupply() {
        final Function function = new Function(FUNC_AIRDROPSUPPLY, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> allowance(String owner, String spender) {
        final Function function = new Function(FUNC_ALLOWANCE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, owner), 
                new org.web3j.abi.datatypes.Address(160, spender)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> approve(String spender, BigInteger value) {
        final Function function = new Function(
                FUNC_APPROVE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, spender), 
                new org.web3j.abi.datatypes.generated.Uint256(value)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> balanceOf(String account) {
        final Function function = new Function(FUNC_BALANCEOF, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, account)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> basePrice() {
        final Function function = new Function(FUNC_BASEPRICE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> buyListedShares(String seller, BigInteger shareId,
            BigInteger amount, BigInteger weiValue) {
        final Function function = new Function(
                FUNC_BUYLISTEDSHARES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, seller), 
                new org.web3j.abi.datatypes.generated.Uint256(shareId), 
                new org.web3j.abi.datatypes.generated.Uint256(amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function, weiValue);
    }

    public RemoteFunctionCall<TransactionReceipt> buyShares(BigInteger amount,
            BigInteger weiValue) {
        final Function function = new Function(
                FUNC_BUYSHARES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function, weiValue);
    }

    public RemoteFunctionCall<Tuple6<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger>> candlesByTimeframe(
            BigInteger param0, BigInteger param1) {
        final Function function = new Function(FUNC_CANDLESBYTIMEFRAME, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(param0), 
                new org.web3j.abi.datatypes.generated.Uint256(param1)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
        return new RemoteFunctionCall<Tuple6<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger>>(function,
                new Callable<Tuple6<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger>>() {
                    @Override
                    public Tuple6<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger> call(
                            ) throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple6<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger>(
                                (BigInteger) results.get(0).getValue(), 
                                (BigInteger) results.get(1).getValue(), 
                                (BigInteger) results.get(2).getValue(), 
                                (BigInteger) results.get(3).getValue(), 
                                (BigInteger) results.get(4).getValue(), 
                                (BigInteger) results.get(5).getValue());
                    }
                });
    }

    public RemoteFunctionCall<BigInteger> currentPrice() {
        final Function function = new Function(FUNC_CURRENTPRICE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> dailyLimit() {
        final Function function = new Function(FUNC_DAILYLIMIT, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> dailyTrades(String param0) {
        final Function function = new Function(FUNC_DAILYTRADES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> decimals() {
        final Function function = new Function(FUNC_DECIMALS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint8>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<String> deployer() {
        final Function function = new Function(FUNC_DEPLOYER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<TransactionReceipt> distributeAirdrop(String user,
            BigInteger amount) {
        final Function function = new Function(
                FUNC_DISTRIBUTEAIRDROP, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, user), 
                new org.web3j.abi.datatypes.generated.Uint256(amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<Tuple6<List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>>> getCandleHistory(
            BigInteger timeframe) {
        final Function function = new Function(FUNC_GETCANDLEHISTORY, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(timeframe)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<Uint256>>() {}, new TypeReference<DynamicArray<Uint256>>() {}, new TypeReference<DynamicArray<Uint256>>() {}, new TypeReference<DynamicArray<Uint256>>() {}, new TypeReference<DynamicArray<Uint256>>() {}, new TypeReference<DynamicArray<Uint256>>() {}));
        return new RemoteFunctionCall<Tuple6<List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>>>(function,
                new Callable<Tuple6<List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>>>() {
                    @Override
                    public Tuple6<List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>> call(
                            ) throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple6<List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>>(
                                convertToNative((List<Uint256>) results.get(0).getValue()), 
                                convertToNative((List<Uint256>) results.get(1).getValue()), 
                                convertToNative((List<Uint256>) results.get(2).getValue()), 
                                convertToNative((List<Uint256>) results.get(3).getValue()), 
                                convertToNative((List<Uint256>) results.get(4).getValue()), 
                                convertToNative((List<Uint256>) results.get(5).getValue()));
                    }
                });
    }

    public RemoteFunctionCall<BigInteger> getCurrentPrice() {
        final Function function = new Function(FUNC_GETCURRENTPRICE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> getEthUsdPrice() {
        final Function function = new Function(FUNC_GETETHUSDPRICE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> getTotalVolumeTraded() {
        final Function function = new Function(FUNC_GETTOTALVOLUMETRADED, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<List> getUsers() {
        final Function function = new Function(FUNC_GETUSERS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<Address>>() {}));
        return new RemoteFunctionCall<List>(function,
                new Callable<List>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List call() throws Exception {
                        List<Type> result = (List<Type>) executeCallSingleValueReturn(function, List.class);
                        return convertToNative(result);
                    }
                });
    }

    public RemoteFunctionCall<BigInteger> globalDailyTrades() {
        final Function function = new Function(FUNC_GLOBALDAILYTRADES, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<Boolean> hasReceivedAirdrop(String param0) {
        final Function function = new Function(FUNC_HASRECEIVEDAIRDROP, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<Boolean> isUser(String param0) {
        final Function function = new Function(FUNC_ISUSER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<BigInteger> lastRecordedDay() {
        final Function function = new Function(FUNC_LASTRECORDEDDAY, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> mintForTrading() {
        final Function function = new Function(
                FUNC_MINTFORTRADING, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<String> name() {
        final Function function = new Function(FUNC_NAME, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<String> owner() {
        final Function function = new Function(FUNC_OWNER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<String> platformAddress() {
        final Function function = new Function(FUNC_PLATFORMADDRESS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<BigInteger> platformSupply() {
        final Function function = new Function(FUNC_PLATFORMSUPPLY, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> priceLimit() {
        final Function function = new Function(FUNC_PRICELIMIT, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> renounceOwnership() {
        final Function function = new Function(
                FUNC_RENOUNCEOWNERSHIP, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> resetDailyLimits(String user) {
        final Function function = new Function(
                FUNC_RESETDAILYLIMITS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, user)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> sellShares(BigInteger amount) {
        final Function function = new Function(
                FUNC_SELLSHARES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> sharesForSale(String param0, BigInteger param1) {
        final Function function = new Function(FUNC_SHARESFORSALE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0), 
                new org.web3j.abi.datatypes.generated.Uint256(param1)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<String> symbol() {
        final Function function = new Function(FUNC_SYMBOL, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<BigInteger> totalSupply() {
        final Function function = new Function(FUNC_TOTALSUPPLY, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> totalVolumeTraded() {
        final Function function = new Function(FUNC_TOTALVOLUMETRADED, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> transfer(String recipient, BigInteger amount) {
        final Function function = new Function(
                FUNC_TRANSFER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, recipient), 
                new org.web3j.abi.datatypes.generated.Uint256(amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> transferFrom(String sender, String recipient,
            BigInteger amount) {
        final Function function = new Function(
                FUNC_TRANSFERFROM, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, sender), 
                new org.web3j.abi.datatypes.Address(160, recipient), 
                new org.web3j.abi.datatypes.generated.Uint256(amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> transferOwnership(String newOwner) {
        final Function function = new Function(
                FUNC_TRANSFEROWNERSHIP, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, newOwner)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> userTradingSupply() {
        final Function function = new Function(FUNC_USERTRADINGSUPPLY, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<String> users(BigInteger param0) {
        final Function function = new Function(FUNC_USERS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    @Deprecated
    public static ArtistSharesToken load(String contractAddress, Web3j web3j,
            Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new ArtistSharesToken(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static ArtistSharesToken load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new ArtistSharesToken(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static ArtistSharesToken load(String contractAddress, Web3j web3j,
            Credentials credentials, ContractGasProvider contractGasProvider) {
        return new ArtistSharesToken(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static ArtistSharesToken load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new ArtistSharesToken(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static class ApprovalEventResponse extends BaseEventResponse {
        public String owner;

        public String spender;

        public BigInteger value;
    }

    public static class CandleUpdatedEventResponse extends BaseEventResponse {
        public BigInteger timeframe;

        public BigInteger timestamp;

        public BigInteger open;

        public BigInteger high;

        public BigInteger low;

        public BigInteger close;

        public BigInteger volume;
    }

    public static class DailyLimitAdjustedEventResponse extends BaseEventResponse {
        public BigInteger newDailyLimit;

        public BigInteger newTargetDailyLimitUsd;

        public BigInteger timestamp;
    }

    public static class DebugLogEventResponse extends BaseEventResponse {
        public String message;

        public BigInteger value1;

        public BigInteger value2;

        public BigInteger value3;
    }

    public static class OwnershipTransferredEventResponse extends BaseEventResponse {
        public String previousOwner;

        public String newOwner;
    }

    public static class PriceUpdatedEventResponse extends BaseEventResponse {
        public BigInteger newPrice;
    }

    public static class SharesBoughtEventResponse extends BaseEventResponse {
        public String buyer;

        public BigInteger amount;

        public BigInteger price;

        public BigInteger timestamp;
    }

    public static class SharesListedEventResponse extends BaseEventResponse {
        public String user;

        public BigInteger shareId;

        public BigInteger price;
    }

    public static class SharesSoldEventResponse extends BaseEventResponse {
        public String seller;

        public BigInteger amount;

        public BigInteger price;

        public BigInteger timestamp;
    }

    public static class TransferEventResponse extends BaseEventResponse {
        public String from;

        public String to;

        public BigInteger value;
    }
}
