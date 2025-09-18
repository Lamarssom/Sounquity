import { useQuery } from '@tanstack/react-query';
import axios from 'axios';

const fetchArtistFinancials = async (artistId) => {
  if (!artistId) throw new Error('Artist ID is required');
  const response = await axios.get(`${import.meta.env.VITE_API_URL}/api/blockchain/financials/${artistId}`);
  return response.data;
};

const fetchBatchFinancials = async (artistIds) => {
  if (!artistIds || artistIds.length === 0) return [];
  const response = await axios.get(`${import.meta.env.VITE_API_URL}/api/blockchain/batch-financials?artistIds=${artistIds.join(',')}`);
  return response.data;
};

export const useArtistFinancials = (artistId) => {
  const { data, error, isLoading } = useQuery({
    queryKey: ['financials', artistId],
    queryFn: () => fetchArtistFinancials(artistId),
    staleTime: 60000, // Cache for 1 minute
    enabled: !!artistId,
  });

  return {
    financials: data || { currentPrice: 'N/A', volume24h: 'N/A', marketCap: 'N/A' },
    isLoading,
    error,
  };
};

export const useBatchArtistFinancials = (artistIds) => {
  const { data, error, isLoading } = useQuery({
    queryKey: ['batchFinancials', artistIds.join(',')],
    queryFn: () => fetchBatchFinancials(artistIds),
    staleTime: 60000,
    enabled: artistIds.length > 0,
  });

  return {
    financials: data || [],
    isLoading,
    error,
  };
};