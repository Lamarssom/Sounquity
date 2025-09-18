// // src/utilities/TradeHistory.js
// import { useQuery } from '@tanstack/react-query';

// const fetchTrades = async (artistId) => {
//     const response = await fetch(`http://localhost:8080/api/trades/artist/${artistId}`);
//     return response.json();
// };

// function TradeHistory({ artistId }) {
//     const { data, error, isLoading } = useQuery(['trades', artistId], () => fetchTrades(artistId));
//     if (isLoading) return <div>Loading...</div>;
//     if (error) return <div>Error: {error.message}</div>;
//     return (
//         <ul>
//             {data.map(trade => (
//                 <li key={trade.id}>
//                     {trade.eventType}: {trade.amount} shares at {trade.price} (ETH: {trade.ethValue})
//                 </li>
//             ))}
//         </ul>
//     );
// }

// export default TradeHistory;