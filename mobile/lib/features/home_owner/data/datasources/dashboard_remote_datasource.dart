import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

class DashboardRemoteDatasource {
  static const _baseUrl = kDebugMode
      ? 'http://10.0.2.2:8080/graphql'
      : 'https://api.barberia.app/graphql';

  final http.Client _client;

  DashboardRemoteDatasource({http.Client? client})
      : _client = client ?? http.Client();

  // ─── Citas del día ─────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getAppointmentsByDate({
    required String date,  // YYYY-MM-DD
    required String token,
  }) async {
    final data = await _query(
      '''
      query Appointments(\$date: String) {
        appointments(date: \$date) {
          id confirmationCode
          client { id name phone email notes }
          service {
            id name category durationMinutes
            basePrice depositAmount
          }
          barberId barberName
          startTime endTime
          status depositStatus depositAmount
          rescheduleCount createdAt
        }
      }
      ''',
      variables: {'date': date},
      token: token,
    );
    final list = data['appointments'] as List? ?? [];
    return list.cast<Map<String, dynamic>>();
  }

  // ─── Mark no-show ──────────────────────────────────────────────────────────

  Future<void> markNoShow({
    required String appointmentId,
    required String token,
  }) async {
    await _mutate(
      '''mutation(\$id: String!) { markNoShow(appointmentId: \$id) }''',
      variables: {'id': appointmentId},
      token: token,
    );
  }

  // ─── Crear cita ────────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> createAppointment({
    required String clientName,
    required String clientPhone,
    required String serviceId,
    required String barberId,
    required String startTime,   // ISO 8601
    required String token,
    String? clientEmail,
  }) async {
    final data = await _mutate(
      '''
      mutation CreateApt(\$input: CreateAppointmentInput!) {
        createAppointment(input: \$input) {
          appointmentId confirmationCode depositStatus paymentUrl
        }
      }
      ''',
      variables: {
        'input': {
          'clientName': clientName,
          'clientPhone': clientPhone,
          'clientEmail': clientEmail,
          'serviceId': serviceId,
          'barberId': barberId,
          'startTime': startTime,
        },
      },
      token: token,
    );
    return data['createAppointment'] as Map<String, dynamic>;
  }

  // ─── Horario ───────────────────────────────────────────────────────────────

  Future<void> setBarberSchedule({
    required String barberId,
    required List<Map<String, dynamic>> schedules,
    required String token,
  }) async {
    await _mutate(
      '''
      mutation SetSchedule(\$input: SetBarberScheduleInputGql!) {
        setBarberSchedule(input: \$input) { dayOfWeek isWorking openTime closeTime capacity }
      }
      ''',
      variables: {
        'input': {
          'barberId': barberId,
          'schedules': schedules,
        },
      },
      token: token,
    );
  }

  // ─── Servicios CRUD ────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> createService({
    required Map<String, dynamic> input,
    required String token,
  }) async {
    final data = await _mutate(
      '''
      mutation CreateSvc(\$input: CreateServiceInput!) {
        createService(input: \$input) {
          id name category durationMinutes basePrice
          depositType depositAmount maxConcurrent isActive
        }
      }
      ''',
      variables: {'input': input},
      token: token,
    );
    return data['createService'] as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> updateService({
    required String serviceId,
    required Map<String, dynamic> input,
    required String token,
  }) async {
    final data = await _mutate(
      '''
      mutation UpdateSvc(\$id: String!, \$input: UpdateServiceInput!) {
        updateService(id: \$id, input: \$input) {
          id name category durationMinutes basePrice
          depositType depositAmount maxConcurrent isActive
        }
      }
      ''',
      variables: {'id': serviceId, 'input': input},
      token: token,
    );
    return data['updateService'] as Map<String, dynamic>;
  }

  Future<void> deleteService({
    required String serviceId,
    required String token,
  }) async {
    await _mutate(
      '''mutation DeleteSvc(\$id: String!) { deleteService(id: \$id) }''',
      variables: {'id': serviceId},
      token: token,
    );
  }

  // ─── HTTP helpers ──────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> _query(
    String q, {
    Map<String, dynamic>? variables,
    required String token,
  }) =>
      _send({'query': q, if (variables != null) 'variables': variables}, token: token);

  Future<Map<String, dynamic>> _mutate(
    String m, {
    Map<String, dynamic>? variables,
    required String token,
  }) =>
      _send({'query': m, if (variables != null) 'variables': variables}, token: token);

  Future<Map<String, dynamic>> _send(
    Map<String, dynamic> body, {
    required String token,
  }) async {
    final response = await _client
        .post(
          Uri.parse(_baseUrl),
          headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer $token',
          },
          body: jsonEncode(body),
        )
        .timeout(const Duration(seconds: 15));

    if (response.statusCode == 401) throw _UnauthorizedException();
    if (response.statusCode != 200) throw _HttpException(response.statusCode);

    final json = jsonDecode(response.body) as Map<String, dynamic>;
    if (json['errors'] != null) {
      final msg = (json['errors'] as List).first['message'] as String;
      throw _GraphQLException(msg);
    }
    return json['data'] as Map<String, dynamic>;
  }
}

class _HttpException implements Exception {
  final int code;
  _HttpException(this.code);
}
class _UnauthorizedException implements Exception {}
class _GraphQLException implements Exception {
  final String message;
  _GraphQLException(this.message);
  @override String toString() => message;
}
