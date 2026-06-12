export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export interface Database {
  public: {
    Tables: {
      devices: {
        Row: {
          id: number
          device_identifier: string
          source: string
          first_seen: string
          last_seen: string
        }
        Insert: {
          id?: number
          device_identifier: string
          source: string
          first_seen?: string
          last_seen?: string
        }
        Update: {
          id?: number
          device_identifier?: string
          source?: string
          first_seen?: string
          last_seen?: string
        }
        Relationships: []
      }
      observations: {
        Row: {
          id: number
          device_id: number
          timestamp: string
          rssi: number
          source: string
        }
        Insert: {
          id?: number
          device_id: number
          timestamp?: string
          rssi: number
          source: string
        }
        Update: {
          id?: number
          device_id?: number
          timestamp?: string
          rssi?: number
          source?: string
        }
        Relationships: [
          {
            foreignKeyName: "observations_device_id_fkey"
            columns: ["device_id"]
            isOneToOne: false
            referencedRelation: "devices"
            referencedColumns: ["id"]
          }
        ]
      }
      sessions: {
        Row: {
          id: number
          device_id: number
          start_time: string
          end_time: string
          duration: number
        }
        Insert: {
          id?: number
          device_id: number
          start_time: string
          end_time: string
          duration?: number
        }
        Update: {
          id?: number
          device_id?: number
          start_time?: string
          end_time?: string
          duration?: number
        }
        Relationships: [
          {
            foreignKeyName: "sessions_device_id_fkey"
            columns: ["device_id"]
            isOneToOne: false
            referencedRelation: "devices"
            referencedColumns: ["id"]
          }
        ]
      }
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
      [_ in never]: never
    }
    Enums: {
      [_ in never]: never
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
}
