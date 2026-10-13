#!/usr/bin/env python3
"""解析佳明 FIT CSV 文件，提取关键消息的字段值"""
import csv
import sys

UNKNOWN = ('?','')

def parse_csv(filepath):
    """解析佳明 FitCSVTool 导出的 CSV 文件"""
    messages = []
    with open(filepath, 'r', encoding='utf-8-sig') as f:
        reader = csv.reader(f)
        for row in reader:
            if len(row) < 3:
                continue
            msg_type = row[0]
            if msg_type != 'Data':
                continue
            local_num = row[1]
            message_name = row[2]
            fields = {}
            i = 3
            while i + 2 < len(row):
                field_name = row[i]
                field_value = row[i+1]
                field_units = row[i+2]
                if field_name and field_value:
                    fields[field_name] = (field_value, field_units)
                i += 3
            messages.append({
                'local_num': local_num,
                'message': message_name,
                'fields': fields
            })
    return messages

def extract_key_fields(messages, target_msg):
    """提取指定消息类型的关键字段"""
    results = []
    for m in messages:
        if m['message'] == target_msg:
            results.append(m['fields'])
    return results

def format_fields(fields, key_fields):
    """格式化输出指定字段"""
    result = []
    for k in key_fields:
        if k in fields:
            val, unit = fields[k]
            if unit:
                result.append("  %s = %s %s" % (k, val, unit))
            else:
                result.append("  %s = %s" % (k, val))
    return result

def fv(fields, key):
    """获取字段值"""
    v = fields.get(key, UNKNOWN)
    return v[0]

def analyze_file(filepath, label):
    print("=" * 80)
    print("【%s】" % label)
    print("文件: %s" % filepath)
    print("=" * 80)

    messages = parse_csv(filepath)

    # 统计各消息类型数量
    msg_counts = {}
    for m in messages:
        name = m['message']
        msg_counts[name] = msg_counts.get(name, 0) + 1
    print("\n消息类型统计:")
    for name, count in sorted(msg_counts.items()):
        print("  %s: %d 条" % (name, count))

    # FileId
    file_ids = extract_key_fields(messages, 'file_id')
    if file_ids:
        print("\n--- FileId ---")
        for f in file_ids:
            print("  serial_number = " + fv(f, 'serial_number'))
            print("  time_created = " + fv(f, 'time_created'))
            print("  manufacturer = " + fv(f, 'manufacturer'))
            print("  garmin_product = " + fv(f, 'garmin_product'))
            print("  type = " + fv(f, 'type'))

    # DeviceInfo
    dev_infos = extract_key_fields(messages, 'device_info')
    if dev_infos:
        print("\n--- DeviceInfo ---")
        for f in dev_infos:
            print("  serial_number = " + fv(f, 'serial_number'))
            print("  manufacturer = " + fv(f, 'manufacturer'))
            print("  garmin_product = " + fv(f, 'garmin_product'))
            print("  product_name = " + fv(f, 'product_name'))

    # Session
    sessions = extract_key_fields(messages, 'session')
    if sessions:
        print("\n--- Session ---")
        for i, s in enumerate(sessions):
            print("  Session #%d:" % i)
            key_fields = [
                'timestamp', 'start_time', 'total_elapsed_time', 'total_timer_time',
                'total_distance', 'total_calories', 'total_ascent', 'total_descent',
                'sport', 'sub_sport', 'avg_heart_rate', 'max_heart_rate',
                'enhanced_avg_speed', 'enhanced_max_speed',
                'avg_running_cadence', 'max_running_cadence',
                'avg_temperature', 'max_temperature', 'min_temperature',
                'first_lap_index', 'num_laps',
                'total_training_effect', 'total_anaerobic_training_effect',
                'total_fractional_ascent', 'total_fractional_descent',
                'total_strides',
                'start_position_lat', 'start_position_long',
                'end_position_lat', 'end_position_long',
            ]
            for line in format_fields(s, key_fields):
                print(line)

    # Lap
    laps = extract_key_fields(messages, 'lap')
    if laps:
        print("\n--- Lap ---")
        for i, l in enumerate(laps):
            print("  Lap #%d:" % i)
            key_fields = [
                'timestamp', 'start_time', 'total_elapsed_time', 'total_timer_time',
                'total_distance', 'total_calories', 'total_ascent', 'total_descent',
                'sport', 'sub_sport', 'avg_heart_rate', 'max_heart_rate',
                'enhanced_avg_speed', 'enhanced_max_speed',
                'avg_running_cadence', 'max_running_cadence',
                'avg_temperature', 'max_temperature', 'min_temperature',
                'lap_trigger',
                'total_fractional_ascent', 'total_fractional_descent',
                'total_strides',
                'start_position_lat', 'start_position_long',
                'end_position_lat', 'end_position_long',
            ]
            for line in format_fields(l, key_fields):
                print(line)

    # Activity
    activities = extract_key_fields(messages, 'activity')
    if activities:
        print("\n--- Activity ---")
        for i, a in enumerate(activities):
            print("  Activity #%d:" % i)
            key_fields = [
                'timestamp', 'total_timer_time', 'num_sessions',
                'type', 'event', 'event_type',
                'local_timestamp',
            ]
            for line in format_fields(a, key_fields):
                print(line)

    # Event
    events = extract_key_fields(messages, 'event')
    if events:
        print("\n--- Event ---")
        for i, e in enumerate(events):
            print("  Event #%d:" % i)
            key_fields = ['timestamp', 'event', 'event_type', 'data', 'group_id']
            for line in format_fields(e, key_fields):
                print(line)

    # Record 统计
    records = extract_key_fields(messages, 'record')
    if records:
        print("\n--- Record (%d 条) ---" % len(records))
        # 显示前3条和最后3条
        print("  前3条 Record:")
        for i in range(min(3, len(records))):
            r = records[i]
            print("    Record #%d:" % i)
            key_fields = [
                'timestamp', 'position_lat', 'position_long', 'altitude',
                'heart_rate', 'cadence', 'speed', 'distance', 'temperature',
                'enhanced_speed', 'enhanced_altitude',
            ]
            for line in format_fields(r, key_fields):
                print("      " + line.strip())
        print("  ...")
        print("  最后3条 Record:")
        for i in range(max(0, len(records)-3), len(records)):
            r = records[i]
            print("    Record #%d:" % i)
            key_fields = [
                'timestamp', 'position_lat', 'position_long', 'altitude',
                'heart_rate', 'cadence', 'speed', 'distance', 'temperature',
                'enhanced_speed', 'enhanced_altitude',
            ]
            for line in format_fields(r, key_fields):
                print("      " + line.strip())

    # Split
    splits = extract_key_fields(messages, 'split')
    if splits:
        print("\n--- Split (%d 条) ---" % len(splits))
        for i, s in enumerate(splits):
            print("  Split #%d:" % i)
            key_fields = [
                'total_elapsed_time', 'total_timer_time', 'total_distance',
                'total_calories', 'total_ascent', 'total_descent',
                'start_time', 'end_time', 'avg_speed', 'max_speed',
                'split_type',
            ]
            for line in format_fields(s, key_fields):
                print(line)

if __name__ == '__main__':
    base = 'output/sample/csv/garmin'

    analyze_file(base + '/394468265_ACTIVITY_fr945.csv', 'FR945 跑步')
    print("\n\n")
    analyze_file(base + '/435004407_ACTIVITY_enduro2.csv', 'Enduro2 跑步')