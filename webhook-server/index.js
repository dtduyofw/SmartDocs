const express = require('express');
const admin = require('firebase-admin');

const app = express();
app.use(express.json());

// Khoi tao Firebase Admin SDK
// Tren may local: doc tu file serviceAccountKey.json
// Tren Render.com: doc tu bien moi truong FIREBASE_SERVICE_ACCOUNT
let serviceAccount;
if (process.env.FIREBASE_SERVICE_ACCOUNT) {
    serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
} else {
    serviceAccount = require('./serviceAccountKey.json');
}
admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
});
const db = admin.firestore();

// Secret token tu Casso (dat trong bien moi truong tren Render/Railway)
const CASSO_SECRET = process.env.CASSO_SECRET || 'doi_secret_nay_truoc_khi_deploy';

// Mapping so tien -> goi va so luot
function xacDinhGoi(amount) {
    if (amount >= 99000) return { loaigoi: 'VIP', tongluot: 999999 };
    if (amount >= 69000) return { loaigoi: 'PRO',  tongluot: 500 };
    if (amount >= 29000) return { loaigoi: 'PLUS', tongluot: 200 };
    return null;
}

// Endpoint nhan webhook tu Casso
app.post('/webhook/casso', async (req, res) => {
    // Kiem tra secret token
    const token = req.headers['secure-token'] || req.headers['authorization'];
    if (token !== CASSO_SECRET) {
        console.warn('Webhook bi tu choi: sai secret token');
        return res.status(401).json({ error: 'Unauthorized' });
    }

    const payload = req.body;

    // Casso gui danh sach giao dich trong field "data"
    const danhSachGiaoDich = payload.data || (payload.id ? [payload] : []);
    if (!danhSachGiaoDich.length) {
        return res.json({ success: true, message: 'Khong co giao dich moi' });
    }

    const ketQua = [];

    for (const tx of danhSachGiaoDich) {
        const soTien = tx.amount || 0;
        const noiDung = (tx.description || tx.memo || '').toUpperCase();
        const maGiaoDich = tx.id || tx.tid || '';

        // Chi xu ly giao dich tien vao (credit)
        if (tx.cusum_balance !== undefined && soTien < 0) continue;

        // Tim ma shortUid trong noi dung chuyen khoan, vi du: "NAP AI SMD123456"
        const match = noiDung.match(/SMD(\d{6})/);
        if (!match) {
            console.log(`Giao dich ${maGiaoDich}: khong co ma SMD, bo qua`);
            ketQua.push({ id: maGiaoDich, status: 'skip', reason: 'Khong co ma SMD' });
            continue;
        }

        const shortUid = 'SMD' + match[1];
        const goiInfo = xacDinhGoi(soTien);

        if (!goiInfo) {
            console.log(`Giao dich ${maGiaoDich}: so tien ${soTien} chua du de nang cap`);
            ketQua.push({ id: maGiaoDich, status: 'skip', reason: 'So tien khong khop goi nao' });
            continue;
        }

        // Tim user theo short_uid tren Firestore
        const snapshot = await db.collection('users')
            .where('short_uid', '==', shortUid)
            .limit(1)
            .get();

        if (snapshot.empty) {
            console.warn(`Khong tim thay user voi shortUid: ${shortUid}`);
            ketQua.push({ id: maGiaoDich, status: 'not_found', shortUid });
            continue;
        }

        const userDoc = snapshot.docs[0];
        const userData = userDoc.data();

        // Tinh tong luot moi: VIP thi set 999999, con lai cong them vao luot hien co
        let tongLuotMoi;
        if (goiInfo.loaigoi === 'VIP') {
            tongLuotMoi = 999999;
        } else {
            tongLuotMoi = (userData.tongluot || 0) + goiInfo.tongluot;
        }

        // Cap nhat Firestore
        await userDoc.ref.update({
            loaigoi: goiInfo.loaigoi,
            tongluot: tongLuotMoi,
            lastUpgradeAt: admin.firestore.FieldValue.serverTimestamp(),
            lastUpgradeAmount: soTien,
            lastUpgradeTxId: maGiaoDich
        });

        console.log(`Nang cap thanh cong: ${shortUid} -> ${goiInfo.loaigoi} (+${goiInfo.tongluot} luot), TX: ${maGiaoDich}`);
        ketQua.push({ id: maGiaoDich, status: 'upgraded', shortUid, goi: goiInfo.loaigoi });
    }

    return res.json({ success: true, results: ketQua });
});

// Health check
app.get('/', (req, res) => {
    res.json({ status: 'SmartDocs Webhook Server dang chay' });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Webhook server khoi dong tren cong ${PORT}`);
});
