import { Card, Page } from '../../components/app/Ui';
import ActivityHistory from '../../components/payments/ActivityHistory';
import { WalletTabs } from '../../components/payments/PaymentUi';
const HistoryPage = () => <Page title="Wallet"><WalletTabs /><Card><ActivityHistory /></Card></Page>;
export default HistoryPage;
