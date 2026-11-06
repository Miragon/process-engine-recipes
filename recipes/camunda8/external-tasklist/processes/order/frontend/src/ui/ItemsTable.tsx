import { formatMoney, type OrderLine } from '../forms/order';

export function ItemsTable({ lines, total, currency }: { lines: OrderLine[]; total: number; currency: string }) {
  return (
    <table className="table">
      <thead>
        <tr>
          <th scope="col">Item</th>
          <th scope="col" className="number">
            Quantity
          </th>
          <th scope="col" className="number">
            Price
          </th>
          <th scope="col" className="number">
            Amount
          </th>
        </tr>
      </thead>
      <tbody>
        {lines.map((line) => (
          <tr key={line.name}>
            <td>{line.name}</td>
            <td className="number">{line.quantity}</td>
            <td className="number">{formatMoney(line.price, currency)}</td>
            <td className="number">{formatMoney(line.price * line.quantity, currency)}</td>
          </tr>
        ))}
      </tbody>
      <tfoot>
        <tr>
          <td colSpan={3}>Total</td>
          <td className="number">{formatMoney(total, currency)}</td>
        </tr>
      </tfoot>
    </table>
  );
}
