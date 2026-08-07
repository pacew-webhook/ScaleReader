from flask import Flask, request, jsonify
import pyautogui

app = Flask(__name__)

@app.route('/api/timbangan', methods=['POST'])
def receive_weight():
    data = request.get_json()
    if not data or 'weight' not in data:
        return jsonify({"status": "error", "message": "Data tidak valid"}), 400

    weight_value = data['weight']
    timestamp = data.get('timestamp', 'Realtime')
    print(f"[{timestamp}]  Data Timbangan Diterima: {weight_value} kg")

    pyautogui.typewrite(str(weight_value))
    pyautogui.press('enter')

    return jsonify({"status": "success", "received": weight_value}), 200

if __name__ == '__main__':
    print(" Server PC berjalan di Port 5000...")
    app.run(host='0.0.0.0', port=5000, debug=True)
