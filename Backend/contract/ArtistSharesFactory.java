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
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
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
public class ArtistSharesFactory extends Contract {
    public static final String BINARY = "Bin file was not provided";

    public static final String FUNC_ARTISTTOTOKEN = "artistToToken";

    public static final String FUNC_CREATEARTISTTOKEN = "createArtistToken";

    public static final String FUNC_DEPLOYEDTOKENS = "deployedTokens";

    public static final String FUNC_GETDEPLOYEDTOKENS = "getDeployedTokens";

    public static final String FUNC_GETPLATFORMADDRESS = "getPlatformAddress";

    public static final String FUNC_GETTOKENBYARTISTID = "getTokenByArtistId";

    public static final String FUNC_PLATFORMADDRESS = "platformAddress";

    public static final String FUNC_PRICEFEEDADDRESS = "priceFeedAddress";

    public static final Event ARTISTTOKENCREATED_EVENT = new Event("ArtistTokenCreated", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Utf8String>() {}, new TypeReference<Utf8String>() {}));
    ;

    @Deprecated
    protected ArtistSharesFactory(String contractAddress, Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected ArtistSharesFactory(String contractAddress, Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected ArtistSharesFactory(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected ArtistSharesFactory(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static List<ArtistTokenCreatedEventResponse> getArtistTokenCreatedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(ARTISTTOKENCREATED_EVENT, transactionReceipt);
        ArrayList<ArtistTokenCreatedEventResponse> responses = new ArrayList<ArtistTokenCreatedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            ArtistTokenCreatedEventResponse typedResponse = new ArtistTokenCreatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.tokenAddress = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.artistId = (String) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.artistName = (String) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static ArtistTokenCreatedEventResponse getArtistTokenCreatedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(ARTISTTOKENCREATED_EVENT, log);
        ArtistTokenCreatedEventResponse typedResponse = new ArtistTokenCreatedEventResponse();
        typedResponse.log = log;
        typedResponse.tokenAddress = (String) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.artistId = (String) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.artistName = (String) eventValues.getNonIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<ArtistTokenCreatedEventResponse> artistTokenCreatedEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getArtistTokenCreatedEventFromLog(log));
    }

    public Flowable<ArtistTokenCreatedEventResponse> artistTokenCreatedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ARTISTTOKENCREATED_EVENT));
        return artistTokenCreatedEventFlowable(filter);
    }

    public RemoteFunctionCall<String> artistToToken(String param0) {
        final Function function = new Function(FUNC_ARTISTTOTOKEN, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<TransactionReceipt> createArtistToken(String artistId, String name,
            String symbol, String teamWallet, BigInteger popularity) {
        final Function function = new Function(
                FUNC_CREATEARTISTTOKEN, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(artistId), 
                new org.web3j.abi.datatypes.Utf8String(name), 
                new org.web3j.abi.datatypes.Utf8String(symbol), 
                new org.web3j.abi.datatypes.Address(160, teamWallet), 
                new org.web3j.abi.datatypes.generated.Uint256(popularity)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<String> deployedTokens(BigInteger param0) {
        final Function function = new Function(FUNC_DEPLOYEDTOKENS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<List> getDeployedTokens() {
        final Function function = new Function(FUNC_GETDEPLOYEDTOKENS, 
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

    public RemoteFunctionCall<String> getPlatformAddress() {
        final Function function = new Function(FUNC_GETPLATFORMADDRESS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<String> getTokenByArtistId(String artistId) {
        final Function function = new Function(FUNC_GETTOKENBYARTISTID, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(artistId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<String> platformAddress() {
        final Function function = new Function(FUNC_PLATFORMADDRESS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<String> priceFeedAddress() {
        final Function function = new Function(FUNC_PRICEFEEDADDRESS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    @Deprecated
    public static ArtistSharesFactory load(String contractAddress, Web3j web3j,
            Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new ArtistSharesFactory(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static ArtistSharesFactory load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new ArtistSharesFactory(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static ArtistSharesFactory load(String contractAddress, Web3j web3j,
            Credentials credentials, ContractGasProvider contractGasProvider) {
        return new ArtistSharesFactory(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static ArtistSharesFactory load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new ArtistSharesFactory(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static class ArtistTokenCreatedEventResponse extends BaseEventResponse {
        public String tokenAddress;

        public String artistId;

        public String artistName;
    }
}
